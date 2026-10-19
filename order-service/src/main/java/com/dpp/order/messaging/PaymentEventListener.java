package com.dpp.order.messaging;

import com.dpp.common.event.EventTypes;
import com.dpp.common.event.Topics;
import com.dpp.order.service.OrderSagaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderSagaService saga;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(OrderSagaService saga, ObjectMapper objectMapper) {
        this.saga = saga;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.PAYMENTS, groupId = "order-service")
    public void onMessage(String message) {
        JsonNode event = read(message);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        String eventType = event.path("eventType").asText();
        UUID orderId = UUID.fromString(event.path("aggregateId").asText());
        String correlationId = event.path("correlationId").asText(null);

        switch (eventType) {
            case EventTypes.PAYMENT_SUCCEEDED -> saga.onPaymentSucceeded(eventId, orderId, correlationId);
            case EventTypes.PAYMENT_FAILED -> saga.onPaymentFailed(eventId, orderId, correlationId);
            case EventTypes.REFUND_COMPLETED -> saga.onRefundCompleted(eventId, orderId, correlationId);
            default -> log.debug("Ignoring payment event of type {}", eventType);
        }
    }

    private JsonNode read(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed payment event", e);
        }
    }
}
