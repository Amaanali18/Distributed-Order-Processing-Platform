package com.dpp.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dpp.payment")
public record PaymentProperties(String provider, Stripe stripe, Reconciliation reconciliation, Resilience resilience) {

    public PaymentProperties {
        if (stripe == null) {
            stripe = new Stripe(null, null, null, null, null);
        }
        if (reconciliation == null) {
            reconciliation = new Reconciliation(null, null);
        }
        if (resilience == null) {
            resilience = new Resilience(null, null, null, null, null, null, null, null);
        }
    }

    public String providerOrDefault() {
        return provider == null || provider.isBlank() ? "simulated" : provider;
    }

    public record Stripe(String apiKey, String webhookSecret, String apiBase, Long connectTimeoutMs, Long readTimeoutMs) {

        public String apiBaseOrDefault() {
            return apiBase == null || apiBase.isBlank() ? "https://api.stripe.com" : apiBase;
        }

        public int connectTimeoutMsOrDefault() {
            return connectTimeoutMs == null || connectTimeoutMs <= 0 ? 2_000 : connectTimeoutMs.intValue();
        }

        public int readTimeoutMsOrDefault() {
            return readTimeoutMs == null || readTimeoutMs <= 0 ? 5_000 : readTimeoutMs.intValue();
        }
    }

    public record Reconciliation(Long intervalMs, Long staleAfterMs) {

        public long intervalMsOrDefault() {
            return intervalMs == null || intervalMs <= 0 ? 60_000L : intervalMs;
        }

        public long staleAfterMsOrDefault() {
            return staleAfterMs == null || staleAfterMs <= 0 ? 120_000L : staleAfterMs;
        }
    }

    public record Resilience(Integer slidingWindowSize,
                            Integer minimumNumberOfCalls,
                            Integer failureRateThreshold,
                            Long waitDurationInOpenStateMs,
                            Long slowCallDurationMs,
                            Integer permittedCallsInHalfOpenState,
                            Integer maxAttempts,
                            Long retryWaitMs) {

        public int slidingWindowSizeOrDefault() {
            return slidingWindowSize == null || slidingWindowSize <= 0 ? 20 : slidingWindowSize;
        }

        public int minimumNumberOfCallsOrDefault() {
            return minimumNumberOfCalls == null || minimumNumberOfCalls <= 0 ? 10 : minimumNumberOfCalls;
        }

        public int failureRateThresholdOrDefault() {
            return failureRateThreshold == null || failureRateThreshold <= 0 ? 50 : failureRateThreshold;
        }

        public long waitDurationInOpenStateMsOrDefault() {
            return waitDurationInOpenStateMs == null || waitDurationInOpenStateMs <= 0 ? 10_000L : waitDurationInOpenStateMs;
        }

        public long slowCallDurationMsOrDefault() {
            return slowCallDurationMs == null || slowCallDurationMs <= 0 ? 3_000L : slowCallDurationMs;
        }

        public int permittedCallsInHalfOpenStateOrDefault() {
            return permittedCallsInHalfOpenState == null || permittedCallsInHalfOpenState <= 0
                    ? 5 : permittedCallsInHalfOpenState;
        }

        public int maxAttemptsOrDefault() {
            return maxAttempts == null || maxAttempts <= 0 ? 3 : maxAttempts;
        }

        public long retryWaitMsOrDefault() {
            return retryWaitMs == null || retryWaitMs <= 0 ? 300L : retryWaitMs;
        }
    }
}
