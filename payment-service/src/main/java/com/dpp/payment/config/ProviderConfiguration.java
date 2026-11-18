package com.dpp.payment.config;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.ResilientPaymentProvider;
import com.dpp.payment.provider.SimulatedPaymentProvider;
import com.dpp.payment.provider.StripePaymentProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class ProviderConfiguration {

    @Bean
    public CircuitBreakerRegistry paymentCircuitBreakerRegistry(PaymentProperties properties) {
        PaymentProperties.Resilience settings = properties.resilience();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSizeOrDefault())
                .minimumNumberOfCalls(settings.minimumNumberOfCallsOrDefault())
                .failureRateThreshold(settings.failureRateThresholdOrDefault())
                .slowCallRateThreshold(80f)
                .slowCallDurationThreshold(Duration.ofMillis(settings.slowCallDurationMsOrDefault()))
                .waitDurationInOpenState(Duration.ofMillis(settings.waitDurationInOpenStateMsOrDefault()))
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenStateOrDefault())
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RetryRegistry paymentRetryRegistry(PaymentProperties properties) {
        PaymentProperties.Resilience settings = properties.resilience();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(settings.maxAttemptsOrDefault())
                .waitDuration(Duration.ofMillis(settings.retryWaitMsOrDefault()))
                .retryOnException(ProviderConfiguration::isRetryable)
                .build();
        return RetryRegistry.of(config);
    }

    @Bean
    public PaymentProvider paymentProvider(PaymentProperties properties,
                                           CircuitBreakerRegistry circuitBreakers,
                                           RetryRegistry retries) {
        PaymentProvider provider = switch (properties.providerOrDefault()) {
            case "stripe" -> new StripePaymentProvider(properties.stripe());
            case "simulated" -> new SimulatedPaymentProvider();
            default -> throw new IllegalStateException(
                    "Unsupported payment provider: " + properties.providerOrDefault());
        };
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker("payment-provider");
        Retry retry = retries.retry("payment-provider");
        return new ResilientPaymentProvider(provider, circuitBreaker, retry);
    }

    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof IOException || throwable instanceof TimeoutException) {
            return true;
        }
        return throwable instanceof ApiException apiException
                && apiException.errorCode() == ErrorCode.SERVICE_UNAVAILABLE;
    }
}
