package com.example.hackathon.dto;

public record DeploymentCheckResponse(
        String name,
        boolean passed,
        boolean required,
        String message
) {
}
