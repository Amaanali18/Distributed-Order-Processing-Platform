package com.dpp.payment.provider;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.Optional;
import java.util.function.Supplier;

public class ResilientPaymentProvider implements PaymentProvider {

    private final PaymentProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientPaymentProvider(PaymentProvider delegate, CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public ProviderPayment createPayment(CreatePaymentCommand command) {
        return guard(() -> delegate.createPayment(command));
    }

    @Override
    public ProviderPayment confirmPayment(ConfirmPaymentCommand command) {
        return guard(() -> delegate.confirmPayment(command));
    }

    @Override
    public Optional<ProviderPayment> findPayment(String providerPaymentId) {
        return guard(() -> delegate.findPayment(providerPaymentId));
    }

    @Override
    public ProviderRefund refund(CreateRefundCommand command) {
        return guard(() -> delegate.refund(command));
    }

    @Override
    public Optional<ProviderRefund> findRefund(String providerRefundId) {
        return guard(() -> delegate.findRefund(providerRefundId));
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        return delegate.verifyWebhookSignature(payload, signatureHeader);
    }

    private <T> T guard(Supplier<T> call) {
        try {
            Supplier<T> guarded = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, call));
            return guarded.get();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Payment provider call failed: " + e.getMessage());
        }
    }
}
