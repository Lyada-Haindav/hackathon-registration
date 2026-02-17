package com.example.hackathon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EventResponse(
        String id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate registrationOpenDate,
        LocalDate registrationCloseDate,
        BigDecimal registrationFee,
        BigDecimal feePerMember,
        int feeSplitMembers,
        boolean active,
        boolean leaderboardVisible,
        boolean onHold
) {
}
