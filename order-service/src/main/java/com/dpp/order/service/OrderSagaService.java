package com.dpp.order.service;

import com.dpp.common.event.EventTypes;
import com.dpp.messaging.ProcessedEventStore;
import com.dpp.order.client.CatalogClient;
import com.dpp.order.domain.Order;
import com.dpp.order.domain.OrderStatus;
import com.dpp.order.messaging.OrderEventPublisher;
import com.dpp.order.repository.OrderRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orders;
    private final CatalogClient catalog;
    private final OrderEventPublisher publisher;
    private final ProcessedEventStore processedEvents;

    public OrderSagaService(OrderRepository orders,
                            CatalogClient catalog,
                            OrderEventPublisher publisher,
                            ProcessedEventStore processedEvents) {
        this.orders = orders;
        this.catalog = catalog;
        this.publisher = publisher;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void onPaymentSucceeded(UUID eventId, UUID orderId, String correlationId) {
        if (!processedEvents.firstTime(eventId, EventTypes.PAYMENT_SUCCEEDED)) {
            log.debug("Duplicate PaymentSucceeded event {} ignored", eventId);
            return;
        }
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("PaymentSucceeded for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("PaymentSucceeded for order {} in status {}, ignoring", orderId, order.getStatus());
            return;
        }

        commitReservation(orderId);
        order.confirm();
        orders.save(order);
        publisher.orderConfirmed(order, correlationId);
        log.info("Order {} confirmed after successful payment", orderId);
    }

    @Transactional
    public void onPaymentFailed(UUID eventId, UUID orderId, String correlationId) {
        if (!processedEvents.firstTime(eventId, EventTypes.PAYMENT_FAILED)) {
            log.debug("Duplicate PaymentFailed event {} ignored", eventId);
            return;
        }
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("PaymentFailed for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("PaymentFailed for order {} in status {}, ignoring", orderId, order.getStatus());
            return;
        }

        releaseReservation(orderId);
        order.cancel();
        orders.save(order);
        publisher.orderCancelled(order, correlationId, "payment failed");
        log.info("Order {} cancelled after failed payment", orderId);
    }

    @Transactional
    public void onRefundCompleted(UUID eventId, UUID orderId, String correlationId) {
        if (!processedEvents.firstTime(eventId, EventTypes.REFUND_COMPLETED)) {
            log.debug("Duplicate RefundCompleted event {} ignored", eventId);
            return;
        }
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("RefundCompleted for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("RefundCompleted for order {} in status {}, ignoring", orderId, order.getStatus());
            return;
        }

        order.refund();
        orders.save(order);
        log.info("Order {} marked refunded", orderId);
    }

    private void commitReservation(UUID orderId) {
        try {
            catalog.commit(orderId);
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to commit reservation for order " + orderId, e);
        }
    }

    private void releaseReservation(UUID orderId) {
        try {
            catalog.release(orderId);
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to release reservation for order " + orderId, e);
        }
    }
}
