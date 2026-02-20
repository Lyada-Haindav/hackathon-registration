package com.example.hackathon.controller;

import com.example.hackathon.dto.AuthResponse;
import com.example.hackathon.dto.FacultyLoginRequest;
import com.example.hackathon.dto.FacultyRegisterRequest;
import com.example.hackathon.dto.ForgotPasswordRequest;
import com.example.hackathon.dto.LoginRequest;
import com.example.hackathon.dto.MessageResponse;
import com.example.hackathon.dto.RegisterRequest;
import com.example.hackathon.dto.ResendVerificationRequest;
import com.example.hackathon.dto.ResetPasswordRequest;
import com.example.hackathon.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    @PostMapping("/faculty/register")
    public ResponseEntity<AuthResponse> registerFaculty(@Valid @RequestBody FacultyRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerFaculty(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/faculty/login")
    public ResponseEntity<AuthResponse> facultyLogin(@Valid @RequestBody FacultyLoginRequest request) {
        return ResponseEntity.ok(authService.facultyLogin(request));
    }

    @GetMapping("/organizer/approve")
    public ResponseEntity<MessageResponse> approveOrganizer(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.approveOrganizer(token));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
