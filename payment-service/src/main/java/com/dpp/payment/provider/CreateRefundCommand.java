package com.dpp.payment.provider;

import java.math.BigDecimal;

public record CreateRefundCommand(
        String idempotencyKey,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        String reason) {
}
