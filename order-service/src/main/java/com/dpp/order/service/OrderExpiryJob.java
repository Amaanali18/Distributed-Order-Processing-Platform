package com.dpp.order.service;

import com.dpp.order.domain.OrderStatus;
import com.dpp.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cancels orders whose payment window has elapsed. Cancellation is published
 * as an OrderCancelled event so payment-service can refund a late success and
 * notification-service can inform the customer.
 */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final long paymentTimeoutMs;

    public OrderExpiryJob(OrderRepository orders,
                          OrderService orderService,
                          @Value("${dpp.orders.payment-timeout-ms:600000}") long paymentTimeoutMs) {
        this.orders = orders;
        this.orderService = orderService;
        this.paymentTimeoutMs = paymentTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${dpp.orders.expiry-sweep-interval-ms:60000}",
            initialDelayString = "${dpp.orders.expiry-sweep-interval-ms:60000}")
    public void expireStaleOrders() {
        Instant cutoff = Instant.now().minusMillis(paymentTimeoutMs);
        List<UUID> staleIds = orders.findIdsByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
        for (UUID id : staleIds) {
            try {
                orderService.expire(id, "order-expiry");
            } catch (Exception e) {
                log.warn("Failed to expire order {}: {}", id, e.getMessage());
            }
        }
        if (!staleIds.isEmpty()) {
            log.info("Expiry sweep considered {} pending order(s)", staleIds.size());
        }
    }
}
