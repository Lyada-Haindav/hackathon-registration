package com.example.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CriterionScoreInput(
        @NotBlank String criterionId,
        @NotNull @PositiveOrZero Double marksGiven
) {
}
