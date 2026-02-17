package com.example.hackathon.dto;

public record TeamMemberResponse(
        String id,
        String name,
        String email,
        String phone,
        String college,
        boolean leader
) {
}
