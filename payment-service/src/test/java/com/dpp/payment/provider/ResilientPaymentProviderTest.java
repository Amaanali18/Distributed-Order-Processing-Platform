package com.dpp.payment.provider;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientPaymentProviderTest {

    private CircuitBreakerRegistry circuitBreakers(int windowSize, int minimumCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    private RetryRegistry retries(int maxAttempts) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .retryOnException(t -> t instanceof ApiException api
                        && api.errorCode() == ErrorCode.SERVICE_UNAVAILABLE)
                .build();
        return RetryRegistry.of(config);
    }

    private PaymentProvider failingDelegate(AtomicInteger calls, int failFirst) {
        return new StubProvider() {
            @Override
            public ProviderPayment createPayment(CreatePaymentCommand command) {
                int n = calls.incrementAndGet();
                if (n <= failFirst) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "provider down");
                }
                return ProviderPayment.succeeded("sim_ok");
            }
        };
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        CircuitBreaker cb = circuitBreakers(20, 10).circuitBreaker("test");
        Retry retry = retries(3).retry("test");
        ResilientPaymentProvider provider =
                new ResilientPaymentProvider(failingDelegate(calls, 2), cb, retry);

        ProviderPayment result = provider.createPayment(
                new CreatePaymentCommand("key", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        new java.math.BigDecimal("10.00"), "USD"));

        assertThat(result.status()).isEqualTo(ProviderPaymentStatus.SUCCEEDED);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void stopsRetryingAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        CircuitBreaker cb = circuitBreakers(20, 10).circuitBreaker("test");
        Retry retry = retries(3).retry("test");
        ResilientPaymentProvider provider =
                new ResilientPaymentProvider(failingDelegate(calls, Integer.MAX_VALUE), cb, retry);

        assertThatThrownBy(() -> provider.createPayment(
                new CreatePaymentCommand("key", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        new java.math.BigDecimal("10.00"), "USD")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void opensCircuitBreakerAfterRepeatedFailures() {
        AtomicInteger calls = new AtomicInteger();
        CircuitBreaker cb = circuitBreakers(2, 2).circuitBreaker("test");
        Retry retry = retries(1).retry("test");
        ResilientPaymentProvider provider =
                new ResilientPaymentProvider(failingDelegate(calls, Integer.MAX_VALUE), cb, retry);

        for (int i = 0; i < 3; i++) {
            try {
                provider.confirmPayment(new ConfirmPaymentCommand("sim_pi", PaymentOutcome.SUCCEEDED));
            } catch (RuntimeException ignored) {
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private abstract static class StubProvider implements PaymentProvider {
        @Override
        public String name() {
            return "stub";
        }

        @Override
        public ProviderPayment createPayment(CreatePaymentCommand command) {
            return ProviderPayment.pending("stub");
        }

        @Override
        public ProviderPayment confirmPayment(ConfirmPaymentCommand command) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "provider down");
        }

        @Override
        public Optional<ProviderPayment> findPayment(String providerPaymentId) {
            return Optional.empty();
        }

        @Override
        public ProviderRefund refund(CreateRefundCommand command) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "provider down");
        }

        @Override
        public Optional<ProviderRefund> findRefund(String providerRefundId) {
            return Optional.empty();
        }

        @Override
        public boolean verifyWebhookSignature(String payload, String signatureHeader) {
            return true;
        }
    }
}
