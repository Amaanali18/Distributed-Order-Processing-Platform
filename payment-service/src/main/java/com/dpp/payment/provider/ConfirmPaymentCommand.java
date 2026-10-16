package com.dpp.payment.provider;

public record ConfirmPaymentCommand(
        String providerPaymentId,
        PaymentOutcome outcome) {
}
