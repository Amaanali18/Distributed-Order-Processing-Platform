package com.dpp.payment.provider;

public record ProviderRefund(
        String providerRefundId,
        ProviderRefundStatus status,
        String failureMessage) {
}
