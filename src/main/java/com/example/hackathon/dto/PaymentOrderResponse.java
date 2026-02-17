package com.example.hackathon.dto;

import java.math.BigDecimal;

public record PaymentOrderResponse(
        String teamId,
        String orderId,
        String keyId,
        BigDecimal amount,
        String currency
) {
}
