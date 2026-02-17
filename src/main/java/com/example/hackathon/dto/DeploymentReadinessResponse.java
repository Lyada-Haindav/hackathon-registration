package com.example.hackathon.dto;

import java.time.Instant;
import java.util.List;

public record DeploymentReadinessResponse(
        boolean ready,
        Instant checkedAt,
        String summary,
        List<DeploymentCheckResponse> checks
) {
}
