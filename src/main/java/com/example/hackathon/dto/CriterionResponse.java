package com.example.hackathon.dto;

public record CriterionResponse(
        String id,
        String eventId,
        String name,
        double maxMarks
) {
}
