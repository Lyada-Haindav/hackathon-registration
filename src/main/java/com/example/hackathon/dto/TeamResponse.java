package com.example.hackathon.dto;

import com.example.hackathon.model.PaymentStatus;

import java.util.List;
import java.util.Map;

public record TeamResponse(
        String teamId,
        String eventId,
        String teamName,
        int teamSize,
        PaymentStatus paymentStatus,
        double totalScore,
        String razorpayOrderId,
        String selectedProblemStatementId,
        String selectedProblemStatementTitle,
        List<TeamMemberResponse> members,
        Map<String, Object> formResponses
) {
}
