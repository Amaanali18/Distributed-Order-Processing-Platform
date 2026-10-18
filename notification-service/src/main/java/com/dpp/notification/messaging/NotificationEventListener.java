package com.dpp.notification.messaging;

import com.dpp.common.event.Topics;
import com.dpp.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationService notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {Topics.ORDERS, Topics.PAYMENTS}, groupId = "notification-service")
    public void onMessage(String message) {
        JsonNode event = read(message);
        String eventType = event.path("eventType").asText();
        if (!NotificationService.isHandled(eventType)) {
            log.debug("Ignoring event of type {}", eventType);
            return;
        }
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        UUID orderId = UUID.fromString(event.path("aggregateId").asText());
        String correlationId = event.path("correlationId").asText(null);
        notifications.handle(eventType, eventId, orderId, event.path("payload"), correlationId);
    }

    private JsonNode read(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed event", e);
        }
    }
}
