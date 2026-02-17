package com.example.hackathon.dto;

public record LeaderboardEntryResponse(
        int rank,
        String teamId,
        String teamName,
        double totalScore
) {
}
