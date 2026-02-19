package com.example.hackathon.service;

import com.example.hackathon.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromEmail;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.email.enabled:false}") boolean emailEnabled,
                        @Value("${app.email.from:no-reply@hackathon.local}") String fromEmail,
                        @Value("${app.web.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromEmail = fromEmail;
        this.baseUrl = baseUrl;
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

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new RuntimeException("Unable to send email: " + ex.getMessage(), ex);
        }
    }
}
