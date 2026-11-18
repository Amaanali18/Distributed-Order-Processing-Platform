package com.dpp.payment.metrics;

import com.dpp.payment.domain.PaymentStatus;
import com.dpp.payment.repository.PaymentAttemptRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMetrics {

    public PaymentStateMetrics(MeterRegistry registry, PaymentAttemptRepository attempts) {
        for (PaymentStatus status : PaymentStatus.values()) {
            registry.gauge("dpp.payments.status", Tags.of("status", status.name()), attempts,
                    repository -> repository.countByStatus(status));
        }
    }
}
