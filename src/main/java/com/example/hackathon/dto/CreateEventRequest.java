package com.example.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalDate registrationOpenDate,
        @NotNull LocalDate registrationCloseDate,
        @NotNull @PositiveOrZero BigDecimal registrationFee,
        boolean active
) {
}
