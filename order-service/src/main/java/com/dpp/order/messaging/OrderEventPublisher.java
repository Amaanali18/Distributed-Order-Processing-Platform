package com.dpp.order.messaging;

import com.dpp.common.event.EventEnvelope;
import com.dpp.common.event.EventTypes;
import com.dpp.common.event.Topics;
import com.dpp.messaging.OutboxWriter;
import com.dpp.order.domain.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderEventPublisher {

    private static final String PRODUCER = "order-service";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxWriter outbox;

    public OrderEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void orderCreated(Order order, String correlationId) {
        publish(EventTypes.ORDER_CREATED, order, correlationId, Map.of(
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "customerEmail", order.getCustomerEmail(),
                "totalAmount", order.getTotalAmount(),
                "currency", order.getCurrency()));
    }

    public void orderConfirmed(Order order, String correlationId) {
        publish(EventTypes.ORDER_CONFIRMED, order, correlationId, Map.of(
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "customerEmail", order.getCustomerEmail()));
    }

    public void orderCancelled(Order order, String correlationId, String reason) {
        publish(EventTypes.ORDER_CANCELLED, order, correlationId, Map.of(
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "customerEmail", order.getCustomerEmail(),
                "reason", reason));
    }

    private void publish(String eventType, Order order, String correlationId, Map<String, Object> payload) {
        EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.of(eventType, SCHEMA_VERSION, order.getId().toString(),
                        correlationId, PRODUCER, payload);
        outbox.enqueue(Topics.ORDERS, order.getId().toString(), envelope);
    }
}
