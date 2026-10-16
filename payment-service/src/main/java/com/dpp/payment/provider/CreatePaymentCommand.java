package com.dpp.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentCommand(
        String idempotencyKey,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency) {
}
