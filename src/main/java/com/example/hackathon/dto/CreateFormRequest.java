package com.example.hackathon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateFormRequest(
        @NotBlank String eventId,
        @NotEmpty @Valid List<FormFieldRequest> fields
) {
}
