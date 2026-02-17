package com.example.hackathon.dto;

import java.time.Instant;

public record EvaluationRecordResponse(
        String evaluationId,
        String teamId,
        String criterionId,
        String criterionName,
        double marksGiven,
        double maxMarks,
        String evaluatedBy,
        String description,
        double totalScore,
        Instant evaluatedAt
) {
}
