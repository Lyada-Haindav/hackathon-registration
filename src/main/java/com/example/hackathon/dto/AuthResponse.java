package com.example.hackathon.dto;

public record AuthResponse(
        String token,
        String userId,
        String name,
        String email,
        String role,
        Boolean emailVerificationRequired,
        String message
) {
    public AuthResponse(String token, String userId, String name, String email, String role) {
        this(token, userId, name, email, role, false, null);
    }
}
