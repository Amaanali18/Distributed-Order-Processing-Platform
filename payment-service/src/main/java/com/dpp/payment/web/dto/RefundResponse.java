package com.dpp.payment.web.dto;

import com.dpp.payment.domain.Refund;
import com.dpp.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String providerRefundId,
        String reason,
        Instant createdAt) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getProviderRefundId(),
                refund.getReason(),
                refund.getCreatedAt());
    }
}
