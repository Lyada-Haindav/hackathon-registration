package com.example.hackathon.dto;

import jakarta.validation.constraints.Size;

public record UpiPaymentConfirmRequest(
        @Size(max = 120) String transactionId
) {
}
