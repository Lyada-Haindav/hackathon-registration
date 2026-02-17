package com.example.hackathon.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FacultyLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String secretCode
) {
}
