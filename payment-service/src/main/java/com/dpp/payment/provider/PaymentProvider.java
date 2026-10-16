package com.dpp.payment.provider;

import java.util.Optional;

public interface PaymentProvider {

    String name();

    ProviderPayment createPayment(CreatePaymentCommand command);

    ProviderPayment confirmPayment(ConfirmPaymentCommand command);

    Optional<ProviderPayment> findPayment(String providerPaymentId);

    ProviderRefund refund(CreateRefundCommand command);

    boolean verifyWebhookSignature(String payload, String signatureHeader);
}
