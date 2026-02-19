package com.example.hackathon.service;

import com.example.hackathon.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromEmail;
    private final String smtpUsername;
    private final String baseUrl;
    private final String brevoApiKey;
    private final String brevoApiUrl;
    private final HttpClient httpClient;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.email.enabled:false}") boolean emailEnabled,
                        @Value("${app.email.from:no-reply@hackathon.local}") String fromEmail,
                        @Value("${spring.mail.username:}") String smtpUsername,
                        @Value("${app.email.brevo.api-key:}") String brevoApiKey,
                        @Value("${app.email.brevo.api-url:https://api.brevo.com/v3/smtp/email}") String brevoApiUrl,
                        @Value("${app.web.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromEmail = fromEmail;
        this.smtpUsername = smtpUsername;
        this.brevoApiKey = brevoApiKey;
        this.brevoApiUrl = brevoApiUrl;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public boolean isEnabled() {
        return emailEnabled;
    }

    public void sendVerificationEmail(User user, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String verifyUrl = baseUrl + "/verify-email?token=" + encodedToken;
        String subject = "Verify your participant account";
        String body = "Hello " + user.getName() + ",\n\n"
                + "Please verify your participant account by clicking the link below:\n"
                + verifyUrl + "\n\n"
                + "If you did not create this account, you can ignore this email.\n\n"
                + "KLH Hackathon Registration";
        sendTextEmail(user.getEmail(), subject, body);
    }

    public void sendPasswordResetEmail(User user, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String resetUrl = baseUrl + "/reset-password?token=" + encodedToken;
        String subject = "Reset your participant password";
        String body = "Hello " + user.getName() + ",\n\n"
                + "You requested to reset your password.\n"
                + "Use the link below to set a new password:\n"
                + resetUrl + "\n\n"
                + "If you did not request this, ignore this email.\n\n"
                + "KLH Hackathon Registration";
        sendTextEmail(user.getEmail(), subject, body);
    }

    private void sendTextEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email service disabled. Subject='{}' To='{}' Body='{}'", subject, to, body);
            return;
        }

        String primaryFrom = selectPrimaryFromAddress();
        String smtpFailureSummary;
        try {
            sendMessage(primaryFrom, to, subject, body);
            return;
        } catch (Exception primaryEx) {
            String fallbackFrom = selectFallbackFromAddress(primaryFrom);
            if (fallbackFrom != null) {
                try {
                    sendMessage(fallbackFrom, to, subject, body);
                    log.warn("Primary EMAIL_FROM '{}' failed. Email delivered using SMTP login sender '{}'.", primaryFrom, fallbackFrom);
                    return;
                } catch (Exception fallbackEx) {
                    smtpFailureSummary = "SMTP delivery failed. primaryFrom='" + primaryFrom + "', fallbackFrom='" + fallbackFrom + "'. "
                            + extractRootMessage(fallbackEx);
                    returnOrThrowWithBrevoFallback(primaryFrom, to, subject, body, smtpFailureSummary, fallbackEx);
                    return;
                }
            }
            smtpFailureSummary = "SMTP delivery failed. from='" + primaryFrom + "'. " + extractRootMessage(primaryEx);
            returnOrThrowWithBrevoFallback(primaryFrom, to, subject, body, smtpFailureSummary, primaryEx);
        }
    }

    private void returnOrThrowWithBrevoFallback(String preferredFrom,
                                                String to,
                                                String subject,
                                                String body,
                                                String smtpFailureSummary,
                                                Exception smtpException) {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            try {
                String apiSender = preferredFrom != null && !preferredFrom.isBlank() ? preferredFrom : smtpUsername;
                sendViaBrevoApi(apiSender, to, subject, body);
                log.warn("SMTP failed but Brevo API fallback succeeded. {}", smtpFailureSummary);
                return;
            } catch (Exception apiEx) {
                throw new RuntimeException(
                        smtpFailureSummary + " | Brevo API fallback failed: " + extractRootMessage(apiEx),
                        apiEx
                );
            }
        }
        throw new RuntimeException(
                smtpFailureSummary + " | Set BREVO_API_KEY to enable HTTPS fallback over port 443.",
                smtpException
        );
    }

    private void sendViaBrevoApi(String sender, String to, String subject, String body) throws Exception {
        String senderEmail = sender;
        if (senderEmail == null || senderEmail.isBlank()) {
            senderEmail = smtpUsername;
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException("Brevo API sender email is missing");
        }

        String payload = "{"
                + "\"sender\":{\"email\":\"" + jsonEscape(senderEmail.trim()) + "\"},"
                + "\"to\":[{\"email\":\"" + jsonEscape(to) + "\"}],"
                + "\"subject\":\"" + jsonEscape(subject) + "\","
                + "\"textContent\":\"" + jsonEscape(body) + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(brevoApiUrl))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("api-key", brevoApiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String bodyText = response.body();
            if (bodyText != null && bodyText.length() > 250) {
                bodyText = bodyText.substring(0, 250) + "...";
            }
            throw new IllegalStateException("Brevo API HTTP " + status + " " + (bodyText == null ? "" : bodyText));
        }
    }

    private void sendMessage(String from, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from.trim());
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String selectPrimaryFromAddress() {
        if (fromEmail != null && !fromEmail.isBlank() && !isPlaceholderValue(fromEmail)) {
            return fromEmail.trim();
        }
        if (smtpUsername != null && !smtpUsername.isBlank() && !isPlaceholderValue(smtpUsername)) {
            return smtpUsername.trim();
        }
        return null;
    }

    private String selectFallbackFromAddress(String primaryFrom) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            return null;
        }
        String fallback = smtpUsername.trim();
        if (primaryFrom == null || primaryFrom.isBlank()) {
            return null;
        }
        return fallback.equalsIgnoreCase(primaryFrom.trim()) ? null : fallback;
    }

    private boolean isPlaceholderValue(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return v.isBlank() || v.contains("<") || v.contains(">") || v.contains("your-");
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return message == null || message.isBlank() ? "Unknown SMTP error" : message;
    }
}
