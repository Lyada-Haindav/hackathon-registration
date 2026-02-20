package com.example.hackathon.service;

import com.example.hackathon.dto.DeploymentCheckResponse;
import com.example.hackathon.dto.DeploymentReadinessResponse;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeploymentReadinessService {

    private final MongoTemplate mongoTemplate;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.payment.mock-enabled:false}")
    private boolean paymentMockEnabled;

    @Value("${app.phonepe.enabled:true}")
    private boolean phonePeEnabled;

    @Value("${app.phonepe.merchant-id:}")
    private String phonePeMerchantId;

    @Value("${app.phonepe.salt-key:}")
    private String phonePeSaltKey;

    @Value("${app.phonepe.salt-index:1}")
    private int phonePeSaltIndex;

    @Value("${app.phonepe.base-url:https://api-preprod.phonepe.com/apis/pg-sandbox}")
    private String phonePeBaseUrl;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:}")
    private String emailFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${app.email.brevo.api-key:}")
    private String brevoApiKey;

    public DeploymentReadinessService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public DeploymentReadinessResponse checkReadiness() {
        List<DeploymentCheckResponse> checks = new ArrayList<>();

        checks.add(checkMongoConnectivity());
        checks.add(checkJwtSecretStrength());
        checks.add(checkPaymentMockMode());
        checks.add(checkPhonePeConfig());
        checks.add(checkEmailSenderConfig());
        checks.add(checkEmailDeliveryConfig());

        boolean ready = checks.stream()
                .filter(DeploymentCheckResponse::required)
                .allMatch(DeploymentCheckResponse::passed);

        String summary = ready
                ? "Deployment readiness passed. System is ready for production rollout."
                : "Deployment readiness failed. Fix required checks before going live.";

        return new DeploymentReadinessResponse(ready, Instant.now(), summary, checks);
    }

    private DeploymentCheckResponse checkMongoConnectivity() {
        try {
            Document pingResult = mongoTemplate.getDb().runCommand(new Document("ping", 1));
            Number okValue = pingResult.get("ok", Number.class);
            boolean ok = okValue != null && okValue.doubleValue() >= 1.0;
            return new DeploymentCheckResponse(
                    "MongoDB connectivity",
                    ok,
                    true,
                    ok ? "MongoDB ping successful." : "MongoDB ping returned an unexpected response."
            );
        } catch (Exception ex) {
            return new DeploymentCheckResponse(
                    "MongoDB connectivity",
                    false,
                    true,
                    "MongoDB connection failed: " + sanitizeMessage(ex.getMessage())
            );
        }
    }

    private DeploymentCheckResponse checkJwtSecretStrength() {
        boolean valid = isStrongSecret(jwtSecret);
        String message = valid
                ? "JWT secret looks production-ready."
                : "JWT_SECRET is weak/default. Use a long random value (at least 32 chars).";
        return new DeploymentCheckResponse("JWT secret strength", valid, true, message);
    }

    private DeploymentCheckResponse checkPaymentMockMode() {
        boolean valid = !paymentMockEnabled;
        String message = valid
                ? "PAYMENT_MOCK_ENABLED is false. Live PhonePe payment flow is enabled."
                : "PAYMENT_MOCK_ENABLED is true. Disable it for live PhonePe payments.";
        return new DeploymentCheckResponse("Payment mock mode", valid, true, message);
    }

    private DeploymentCheckResponse checkPhonePeConfig() {
        if (!phonePeEnabled) {
            return new DeploymentCheckResponse(
                    "PhonePe merchant config",
                    false,
                    true,
                    "PHONEPE_ENABLED is false. Enable PhonePe payments for production."
            );
        }

        boolean valid = isConfigured(phonePeMerchantId)
                && isConfigured(phonePeSaltKey)
                && phonePeSaltIndex > 0
                && isConfigured(phonePeBaseUrl);

        String message = valid
                ? "PhonePe merchant credentials are configured."
                : "Set PHONEPE_MERCHANT_ID, PHONEPE_SALT_KEY, PHONEPE_SALT_INDEX, and PHONEPE_BASE_URL.";
        return new DeploymentCheckResponse("PhonePe merchant config", valid, true, message);
    }

    private DeploymentCheckResponse checkEmailSenderConfig() {
        if (!emailEnabled) {
            return new DeploymentCheckResponse(
                    "Email sender configured",
                    true,
                    false,
                    "EMAIL_ENABLED is false. Email checks are skipped."
            );
        }
        boolean valid = isValidEmailSender(emailFrom);
        return new DeploymentCheckResponse(
                "Email sender configured",
                valid,
                true,
                valid
                        ? "EMAIL_FROM is configured with a valid sender."
                        : "Set EMAIL_FROM to a verified sender email in Brevo."
        );
    }

    private DeploymentCheckResponse checkEmailDeliveryConfig() {
        if (!emailEnabled) {
            return new DeploymentCheckResponse(
                    "Email delivery path",
                    true,
                    false,
                    "EMAIL_ENABLED is false. Email checks are skipped."
            );
        }
        boolean smtpConfigured = isNonBlank(smtpUsername) && isNonBlank(smtpPassword);
        boolean brevoApiConfigured = isNonBlank(brevoApiKey);
        boolean valid = smtpConfigured || brevoApiConfigured;
        String message;
        if (valid) {
            message = smtpConfigured
                    ? "SMTP credentials are configured."
                    : "BREVO_API_KEY is configured for HTTPS email fallback.";
        } else {
            message = "Configure SMTP_USERNAME/SMTP_PASSWORD or BREVO_API_KEY.";
        }
        return new DeploymentCheckResponse("Email delivery path", valid, true, message);
    }

    private boolean isStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        String normalized = secret.toLowerCase();
        if (normalized.contains("replace") || normalized.contains("secret") || normalized.contains("changeme")) {
            return false;
        }
        return secret.length() >= 32;
    }

    private boolean isValidEmailSender(String sender) {
        if (!isNonBlank(sender)) {
            return false;
        }
        String normalized = sender.trim().toLowerCase();
        if (normalized.contains("<") || normalized.contains(">") || normalized.contains("your-")
                || normalized.contains("replace") || normalized.contains("example")) {
            return false;
        }
        return normalized.contains("@");
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isConfigured(String value) {
        if (!isNonBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.contains("replace")
                && !normalized.contains("example")
                && !normalized.contains("your_");
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No error details available";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 220 ? oneLine.substring(0, 220) + "..." : oneLine;
    }
}
