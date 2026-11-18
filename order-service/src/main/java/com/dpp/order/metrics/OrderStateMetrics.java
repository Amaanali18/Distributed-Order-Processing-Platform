package com.dpp.order.metrics;

import com.dpp.order.domain.OrderStatus;
import com.dpp.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMetrics {

    public OrderStateMetrics(MeterRegistry registry, OrderRepository orders) {
        for (OrderStatus status : OrderStatus.values()) {
            registry.gauge("dpp.orders.status", Tags.of("status", status.name()), orders,
                    repository -> repository.countByStatus(status));
        }
    }
}
