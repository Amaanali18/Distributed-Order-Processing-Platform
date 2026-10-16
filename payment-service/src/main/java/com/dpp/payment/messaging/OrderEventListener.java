package com.dpp.payment.messaging;

import com.dpp.common.event.EventTypes;
import com.dpp.common.event.Topics;
import com.dpp.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDERS, groupId = "payment-service")
    public void onMessage(String message) {
        JsonNode event = read(message);
        String eventType = event.path("eventType").asText();
        if (!EventTypes.ORDER_CANCELLED.equals(eventType)) {
            log.debug("Ignoring order event of type {}", eventType);
            return;
        }
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        UUID orderId = UUID.fromString(event.path("aggregateId").asText());
        String correlationId = event.path("correlationId").asText(null);
        paymentService.onOrderCancelled(eventId, orderId, correlationId);
    }

    private JsonNode read(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed order event", e);
        }
    }
}
