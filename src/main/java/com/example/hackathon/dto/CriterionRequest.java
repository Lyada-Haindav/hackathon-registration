package com.example.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CriterionRequest(
        @NotBlank String eventId,
        @NotBlank String name,
        @NotNull @Positive Double maxMarks
) {
}
