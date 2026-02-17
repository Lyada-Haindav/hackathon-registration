package com.example.hackathon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvaluateTeamRequest(
        @NotBlank String eventId,
        @NotBlank String teamId,
        @Size(max = 1000) String description,
        @NotEmpty @Valid List<CriterionScoreInput> scores
) {
}
