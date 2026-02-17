package com.example.hackathon.service;

import com.example.hackathon.dto.DeploymentCheckResponse;
import com.example.hackathon.dto.DeploymentReadinessResponse;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.bson.Document;
import org.json.JSONObject;
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

    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    public DeploymentReadinessService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public DeploymentReadinessResponse checkReadiness() {
        List<DeploymentCheckResponse> checks = new ArrayList<>();

        checks.add(checkMongoConnectivity());
        checks.add(checkJwtSecretStrength());
        checks.add(checkPaymentMockMode());

        boolean razorpayConfigured = isConfiguredForProduction(razorpayKeyId) && isConfiguredForProduction(razorpayKeySecret);
        checks.add(new DeploymentCheckResponse(
                "Razorpay keys configured",
                razorpayConfigured,
                true,
                razorpayConfigured
                        ? "RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are configured."
                        : "Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to real credentials."
        ));

        if (razorpayConfigured && !paymentMockEnabled) {
            checks.add(checkRazorpayConnectivity());
        } else if (paymentMockEnabled) {
            checks.add(new DeploymentCheckResponse(
                    "Razorpay API authentication",
                    false,
                    true,
                    "Skipped because PAYMENT_MOCK_ENABLED is true. Disable mock mode for real payments."
            ));
        } else {
            checks.add(new DeploymentCheckResponse(
                    "Razorpay API authentication",
                    false,
                    true,
                    "Skipped because Razorpay keys are not configured."
            ));
        }

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
                ? "PAYMENT_MOCK_ENABLED is false. Real payment flow is enabled."
                : "PAYMENT_MOCK_ENABLED is true. Disable it for live Razorpay transactions.";
        return new DeploymentCheckResponse("Payment mock mode", valid, true, message);
    }

    private DeploymentCheckResponse checkRazorpayConnectivity() {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("count", 1);
            client.payments.fetchAll(options);
            return new DeploymentCheckResponse(
                    "Razorpay API authentication",
                    true,
                    true,
                    "Razorpay credentials validated by API call."
            );
        } catch (RazorpayException ex) {
            return new DeploymentCheckResponse(
                    "Razorpay API authentication",
                    false,
                    true,
                    "Razorpay API auth failed: " + sanitizeMessage(ex.getMessage())
            );
        } catch (Exception ex) {
            return new DeploymentCheckResponse(
                    "Razorpay API authentication",
                    false,
                    true,
                    "Razorpay connectivity check failed: " + sanitizeMessage(ex.getMessage())
            );
        }
    }

    private boolean isConfiguredForProduction(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.contains("replace")
                && !normalized.contains("your_")
                && !normalized.contains("example")
                && !normalized.contains("rzp_test_replace");
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

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No error details available";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 220 ? oneLine.substring(0, 220) + "..." : oneLine;
    }
}
