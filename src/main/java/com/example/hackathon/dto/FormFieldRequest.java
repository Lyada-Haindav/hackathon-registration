package com.example.hackathon.dto;

import com.example.hackathon.model.FieldType;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public record FormFieldRequest(
        @NotBlank String key,
        @NotBlank String label,
        FieldType type,
        boolean required,
        List<String> options
) {
    public FormFieldRequest {
        options = options == null ? new ArrayList<>() : options;
        type = type == null ? FieldType.TEXT : type;
    }
}
