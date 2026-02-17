package com.example.hackathon.dto;

import java.time.Instant;

public record ProblemStatementResponse(
        String id,
        String eventId,
        String title,
        String description,
        boolean released,
        Instant releasedAt
) {
}
