package com.dpp.payment.config;

import com.dpp.payment.provider.PaymentProvider;
import com.dpp.payment.provider.SimulatedPaymentProvider;
import com.dpp.payment.provider.StripePaymentProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class ProviderConfiguration {

    @Bean
    public PaymentProvider paymentProvider(PaymentProperties properties) {
        return switch (properties.providerOrDefault()) {
            case "stripe" -> new StripePaymentProvider(properties.stripe());
            case "simulated" -> new SimulatedPaymentProvider();
            default -> throw new IllegalStateException(
                    "Unsupported payment provider: " + properties.providerOrDefault());
        };
    }
}
