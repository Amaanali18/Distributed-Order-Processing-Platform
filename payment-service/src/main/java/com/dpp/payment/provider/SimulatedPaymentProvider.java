package com.dpp.payment.provider;

import com.dpp.common.error.ApiException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimulatedPaymentProvider implements PaymentProvider {

    private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public ProviderPayment createPayment(CreatePaymentCommand command) {
        String providerPaymentId = "sim_pi_" + UUID.randomUUID();
        ProviderPayment payment = ProviderPayment.pending(providerPaymentId);
        payments.put(providerPaymentId, payment);
        return payment;
    }

    @Override
    public ProviderPayment confirmPayment(ConfirmPaymentCommand command) {
        ProviderPayment known = payments.get(command.providerPaymentId());
        if (known == null) {
            throw ApiException.notFound("Unknown simulated payment " + command.providerPaymentId());
        }
        if (known.status() != ProviderPaymentStatus.PENDING) {
            return known;
        }
        ProviderPayment result = command.outcome() == PaymentOutcome.FAILED
                ? ProviderPayment.failed(command.providerPaymentId(), "card_declined", "The simulated card was declined")
                : ProviderPayment.succeeded(command.providerPaymentId());
        payments.put(command.providerPaymentId(), result);
        return result;
    }

    @Override
    public Optional<ProviderPayment> findPayment(String providerPaymentId) {
        return Optional.ofNullable(payments.get(providerPaymentId));
    }

    @Override
    public ProviderRefund refund(CreateRefundCommand command) {
        return new ProviderRefund("sim_re_" + UUID.randomUUID(), ProviderRefundStatus.SUCCEEDED, null);
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        return true;
    }
}
