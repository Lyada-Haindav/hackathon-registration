package com.example.hackathon.service;

import com.example.hackathon.dto.AuthResponse;
import com.example.hackathon.dto.FacultyLoginRequest;
import com.example.hackathon.dto.FacultyRegisterRequest;
import com.example.hackathon.dto.ForgotPasswordRequest;
import com.example.hackathon.dto.LoginRequest;
import com.example.hackathon.dto.MessageResponse;
import com.example.hackathon.dto.RegisterRequest;
import com.example.hackathon.dto.ResendVerificationRequest;
import com.example.hackathon.dto.ResetPasswordRequest;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.AuthTokenType;
import com.example.hackathon.model.Role;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.UserRepository;
import com.example.hackathon.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final String facultySecretCode;
    private final boolean userEmailVerificationRequired;
    private final long emailVerificationTtlMinutes;
    private final long passwordResetTtlMinutes;
    private final long organizerApprovalTtlMinutes;
    private final String organizerOwnerEmail;

    public AuthService(UserRepository userRepository,
                       UserService userService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AuthTokenService authTokenService,
                       EmailService emailService,
                       @Value("${app.faculty.secret-code:KLHAZ}") String facultySecretCode,
                       @Value("${app.auth.user-email-verification-required:true}") boolean userEmailVerificationRequired,
                       @Value("${app.auth.email-verification-ttl-minutes:1440}") long emailVerificationTtlMinutes,
                       @Value("${app.auth.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes,
                       @Value("${app.auth.organizer-approval-ttl-minutes:10080}") long organizerApprovalTtlMinutes,
                       @Value("${app.admin.email:}") String organizerOwnerEmail) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.facultySecretCode = facultySecretCode;
        this.userEmailVerificationRequired = userEmailVerificationRequired;
        this.emailVerificationTtlMinutes = emailVerificationTtlMinutes;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.organizerApprovalTtlMinutes = organizerApprovalTtlMinutes;
        this.organizerOwnerEmail = organizerOwnerEmail;
    }

    public AuthResponse registerUser(RegisterRequest request) {
        return registerWithRole(request, Role.USER);
    }

    public AuthResponse registerFaculty(FacultyRegisterRequest request) {
        if (!facultySecretCode.equals(request.secretCode())) {
            throw new BadRequestException("Invalid organizer secret code");
        }

        ensureEmailServiceEnabled();
        String normalizedEmail = userService.normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            User existing = userService.findByEmail(normalizedEmail);
            if (existing.getRole() == Role.FACULTY && !existing.isActive()) {
                sendOrganizerApprovalMail(existing);
                return new AuthResponse(
                        null,
                        existing.getId(),
                        existing.getName(),
                        existing.getEmail(),
                        existing.getRole().name(),
                        false,
                        "Organiser registration is pending owner approval. Approval email sent to owner."
                );
            }
            throw new BadRequestException("Email is already registered");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.FACULTY);
        user.setEmailVerified(true);
        user.setActive(false);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Email is already registered");
        }
        userService.evictUserCacheByEmail(saved.getEmail());

        try {
            sendOrganizerApprovalMail(saved);
        } catch (BadRequestException ex) {
            userRepository.deleteById(saved.getId());
            userService.evictUserCacheByEmail(saved.getEmail());
            throw ex;
        }

        return new AuthResponse(
                null,
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getRole().name(),
                false,
                "Registration submitted. Owner approval is required before organiser login."
        );
    }

    public AuthResponse login(LoginRequest request) {
        String email = userService.normalizeEmail(request.email());
        User user = userService.findByEmail(email);
        if (user.getRole() == Role.FACULTY) {
            throw new BadRequestException("Organizer must use the organizer login page with secret code");
        }
        if (!user.isEmailVerified()) {
            throw new BadRequestException("Please verify your email before login. Use resend verification to get a new link.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid email or password");
        } catch (DisabledException ex) {
            throw new BadRequestException("Account is not active for login");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse facultyLogin(FacultyLoginRequest request) {
        if (!facultySecretCode.equals(request.secretCode())) {
            throw new BadRequestException("Invalid organizer secret code");
        }

        String email = userService.normalizeEmail(request.email());
        User user = userService.findByEmail(email);
        if (user.getRole() != Role.FACULTY) {
            throw new BadRequestException("Only organizer accounts can use this login");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Organizer account is pending owner approval. Please wait for approval email.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid email or password");
        } catch (DisabledException ex) {
            throw new BadRequestException("Account is not active for login");
        }

        return buildAuthResponse(user);
    }

    public User ensureFacultyUser(String name, String email, String rawPassword) {
        String normalizedEmail = userService.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return userService.findByEmail(normalizedEmail);
        }

        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.FACULTY);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        userService.evictUserCacheByEmail(saved.getEmail());
        return saved;
    }

    public MessageResponse verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verification token is required");
        }

        var authToken = authTokenService.getValidToken(token, AuthTokenType.EMAIL_VERIFICATION);
        User user = userService.findById(authToken.getUserId());
        if (user.getRole() != Role.USER) {
            throw new BadRequestException("Verification is available only for participant accounts");
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        userService.evictUserCacheByEmail(user.getEmail());
        authTokenService.consumeToken(token, AuthTokenType.EMAIL_VERIFICATION);

        return new MessageResponse("Email verified successfully. You can login now.");
    }

    public MessageResponse resendVerification(ResendVerificationRequest request) {
        String email = userService.normalizeEmail(request.email());
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (Exception ignored) {
            return new MessageResponse("If this email is registered, a verification link has been sent.");
        }

        if (user.getRole() != Role.USER) {
            return new MessageResponse("If this email is registered, a verification link has been sent.");
        }

        if (user.isEmailVerified()) {
            return new MessageResponse("This participant email is already verified. Please login.");
        }

        ensureEmailServiceEnabled();
        sendVerificationMail(user);
        return new MessageResponse("Verification link sent. Please check your email.");
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = userService.normalizeEmail(request.email());
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (Exception ignored) {
            return new MessageResponse("If this email is registered, a password reset link has been sent.");
        }

        if (user.getRole() != Role.USER) {
            return new MessageResponse("If this email is registered, a password reset link has been sent.");
        }

        ensureEmailServiceEnabled();
        sendPasswordResetMail(user);
        return new MessageResponse("If this email is registered, a password reset link has been sent.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        var authToken = authTokenService.getValidToken(request.token(), AuthTokenType.PASSWORD_RESET);
        User user = userService.findById(authToken.getUserId());
        if (user.getRole() != Role.USER) {
            throw new BadRequestException("Password reset is available only for participant accounts");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        userService.evictUserCacheByEmail(user.getEmail());
        authTokenService.consumeToken(request.token(), AuthTokenType.PASSWORD_RESET);

        return new MessageResponse("Password updated successfully. Please login with your new password.");
    }

    public MessageResponse approveOrganizer(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Approval token is required");
        }

        var authToken = authTokenService.getValidToken(token, AuthTokenType.ORGANIZER_APPROVAL);
        User user = userService.findById(authToken.getUserId());
        if (user.getRole() != Role.FACULTY) {
            throw new BadRequestException("Approval link is valid only for organizer accounts");
        }

        if (user.isActive()) {
            authTokenService.consumeToken(token, AuthTokenType.ORGANIZER_APPROVAL);
            return new MessageResponse("Organiser is already approved.");
        }

        user.setActive(true);
        userRepository.save(user);
        userService.evictUserCacheByEmail(user.getEmail());
        authTokenService.consumeToken(token, AuthTokenType.ORGANIZER_APPROVAL);

        try {
            emailService.sendOrganizerApprovedEmail(user);
        } catch (RuntimeException ex) {
            log.warn("Organizer '{}' approved but confirmation email failed: {}", user.getEmail(), ex.getMessage());
        }

        return new MessageResponse("Organiser approved successfully. This organiser can now login.");
    }

    private AuthResponse registerWithRole(RegisterRequest request, Role role) {
        String normalizedEmail = userService.normalizeEmail(request.email());
        if (role == Role.USER) {
            ensureEmailServiceEnabled();
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            User existing = userService.findByEmail(normalizedEmail);
            if (role == Role.USER
                    && existing.getRole() == Role.USER
                    && !existing.isEmailVerified()) {
                sendVerificationMail(existing);
                return new AuthResponse(
                        null,
                        existing.getId(),
                        existing.getName(),
                        existing.getEmail(),
                        existing.getRole().name(),
                        true,
                        "Email already registered but not verified. New verification link sent."
                );
            }
            throw new BadRequestException("Email is already registered");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        if (role == Role.FACULTY) {
            user.setEmailVerified(true);
        } else {
            user.setEmailVerified(false);
        }

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Email is already registered");
        }
        userService.evictUserCacheByEmail(saved.getEmail());

        if (saved.getRole() == Role.USER && !saved.isEmailVerified()) {
            try {
                sendVerificationMail(saved);
            } catch (BadRequestException ex) {
                userRepository.deleteById(saved.getId());
                userService.evictUserCacheByEmail(saved.getEmail());
                throw ex;
            }
            return new AuthResponse(
                    null,
                    saved.getId(),
                    saved.getName(),
                    saved.getEmail(),
                    saved.getRole().name(),
                    true,
                    "Verification email sent. Please verify your email before login."
            );
        }

        return buildAuthResponse(saved);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                ),
                Map.of("role", user.getRole().name(), "userId", user.getId())
        );

        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

    private void sendVerificationMail(User user) {
        String token = authTokenService.createToken(user, AuthTokenType.EMAIL_VERIFICATION, emailVerificationTtlMinutes);
        try {
            emailService.sendVerificationEmail(user, token);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Unable to send verification email. "
                    + buildEmailTroubleshootingMessage(ex));
        }
    }

    private void sendPasswordResetMail(User user) {
        String token = authTokenService.createToken(user, AuthTokenType.PASSWORD_RESET, passwordResetTtlMinutes);
        try {
            emailService.sendPasswordResetEmail(user, token);
        } catch (RuntimeException ex) {
            try {
                authTokenService.consumeToken(token, AuthTokenType.PASSWORD_RESET);
            } catch (Exception ignored) {
                // best effort cleanup for failed email deliveries
            }
            throw new BadRequestException("Unable to send password reset email. "
                    + buildEmailTroubleshootingMessage(ex));
        }
    }

    private void sendOrganizerApprovalMail(User user) {
        String ownerEmail = userService.normalizeEmail(organizerOwnerEmail);
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new BadRequestException("Owner email is not configured. Set ADMIN_EMAIL.");
        }

        String token = authTokenService.createToken(user, AuthTokenType.ORGANIZER_APPROVAL, organizerApprovalTtlMinutes);
        try {
            emailService.sendOrganizerApprovalRequestEmail(ownerEmail, user, token);
        } catch (RuntimeException ex) {
            try {
                authTokenService.consumeToken(token, AuthTokenType.ORGANIZER_APPROVAL);
            } catch (Exception ignored) {
                // best effort cleanup for failed email deliveries
            }
            throw new BadRequestException("Unable to send organizer approval email. "
                    + buildEmailTroubleshootingMessage(ex));
        }
    }

    private void ensureEmailServiceEnabled() {
        if (!emailService.isEnabled()) {
            throw new BadRequestException("Email service is not configured. Set EMAIL_ENABLED and SMTP settings.");
        }
    }

    private String buildEmailTroubleshootingMessage(RuntimeException ex) {
        String details = ex.getMessage();
        if (details == null || details.isBlank()) {
            details = "Unknown SMTP error";
        }
        if (details.length() > 220) {
            details = details.substring(0, 220) + "...";
        }
        return "Check SMTP credentials, EMAIL_FROM (verified sender in Brevo), APP_BASE_URL, or set BREVO_API_KEY for HTTPS fallback. Details: " + details;
    }
}
