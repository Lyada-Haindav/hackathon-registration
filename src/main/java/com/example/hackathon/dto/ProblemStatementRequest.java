package com.example.hackathon.dto;

import jakarta.validation.constraints.NotBlank;

public record ProblemStatementRequest(
        @NotBlank String eventId,
        @NotBlank String title,
        @NotBlank String description
) {
}
