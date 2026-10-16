package com.dpp.payment.web.dto;

import com.dpp.payment.domain.PaymentAttempt;
import com.dpp.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        String providerPaymentId,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(PaymentAttempt attempt) {
        return new PaymentResponse(
                attempt.getId(),
                attempt.getOrderId(),
                attempt.getCustomerId(),
                attempt.getStatus(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getProvider(),
                attempt.getProviderPaymentId(),
                attempt.getFailureCode(),
                attempt.getFailureMessage(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }
}
