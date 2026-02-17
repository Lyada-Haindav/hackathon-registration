package com.example.hackathon.dto;

import com.example.hackathon.model.PaymentStatus;

public record PaymentVerificationResponse(
        String teamId,
        PaymentStatus paymentStatus,
        String message
) {
}
