package com.dpp.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dpp.payment")
public record PaymentProperties(String provider, Stripe stripe, Reconciliation reconciliation) {

    public String providerOrDefault() {
        return provider == null || provider.isBlank() ? "simulated" : provider;
    }

    public record Stripe(String apiKey, String webhookSecret, String apiBase) {

        public String apiBaseOrDefault() {
            return apiBase == null || apiBase.isBlank() ? "https://api.stripe.com" : apiBase;
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
}
