package com.example.hackathon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TeamRegistrationRequest(
        @NotBlank String eventId,
        @NotEmpty @Size(min = 1, max = 4) @Valid List<TeamMemberRequest> members,
        Map<String, Object> formResponses
) {
    public TeamRegistrationRequest {
        formResponses = formResponses == null ? new HashMap<>() : formResponses;
    }
}
