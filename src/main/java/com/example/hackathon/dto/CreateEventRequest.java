package com.example.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String description,
        @Size(max = 2500) String aboutEvent,
        @Size(max = 3500000) String posterUrl,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalDate registrationOpenDate,
        @NotNull LocalDate registrationCloseDate,
        @NotNull @PositiveOrZero BigDecimal registrationFee,
        boolean active
) {
}
