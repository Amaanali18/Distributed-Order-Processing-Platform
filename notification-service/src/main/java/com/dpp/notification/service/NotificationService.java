package com.dpp.notification.service;

import com.dpp.common.event.EventTypes;
import com.dpp.messaging.ProcessedEventStore;
import com.dpp.notification.domain.Notification;
import com.dpp.notification.domain.NotificationRecipient;
import com.dpp.notification.repository.NotificationRecipientRepository;
import com.dpp.notification.repository.NotificationRepository;
import com.dpp.notification.sender.NotificationSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Set<String> HANDLED = Set.of(
            EventTypes.ORDER_CREATED,
            EventTypes.ORDER_CONFIRMED,
            EventTypes.ORDER_CANCELLED,
            EventTypes.PAYMENT_SUCCEEDED,
            EventTypes.PAYMENT_FAILED,
            EventTypes.REFUND_COMPLETED);

    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final ProcessedEventStore processedEvents;
    private final NotificationRenderer renderer;
    private final NotificationSender sender;

    public NotificationService(NotificationRepository notifications,
                               NotificationRecipientRepository recipients,
                               ProcessedEventStore processedEvents,
                               NotificationRenderer renderer,
                               NotificationSender sender) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.processedEvents = processedEvents;
        this.renderer = renderer;
        this.sender = sender;
    }

    public static boolean isHandled(String eventType) {
        return HANDLED.contains(eventType);
    }

    @Transactional
    public void handle(String eventType, UUID eventId, UUID orderId, JsonNode payload, String correlationId) {
        if (!isHandled(eventType)) {
            return;
        }
        if (!processedEvents.firstTime(eventId, eventType)) {
            log.debug("Duplicate {} event {} ignored", eventType, eventId);
            return;
        }

        if (EventTypes.ORDER_CREATED.equals(eventType)) {
            upsertRecipient(orderId, payload);
        }

        Recipient target = resolveRecipient(orderId, payload);
        NotificationRenderer.Rendered rendered = renderer.render(eventType, orderId, payload);
        Notification notification = Notification.pending(eventId, eventType, orderId,
                target.customerId(), target.email(), sender.channel(),
                rendered.subject(), rendered.body());
        notifications.save(notification);
        deliver(notification);
        log.info("Recorded {} notification for order {} (customer {})",
                eventType, orderId, target.customerId());
    }

    private void deliver(Notification notification) {
        try {
            sender.send(notification);
            notification.markSent();
        } catch (RuntimeException e) {
            notification.markFailed(e.getMessage());
            log.warn("Delivery of notification {} failed: {}", notification.getId(), e.getMessage());
        }
        notifications.save(notification);
    }

    private void upsertRecipient(UUID orderId, JsonNode payload) {
        UUID customerId = uuid(payload, "customerId");
        String email = blankToNull(payload.path("customerEmail").asText(null));
        NotificationRecipient recipient = recipients.findById(orderId).orElse(null);
        if (recipient == null) {
            recipients.save(NotificationRecipient.of(orderId, customerId, email));
        } else {
            recipient.update(customerId, email);
            recipients.save(recipient);
        }
    }

    private Recipient resolveRecipient(UUID orderId, JsonNode payload) {
        UUID customerId = uuid(payload, "customerId");
        String email = blankToNull(payload.path("customerEmail").asText(null));
        if (customerId == null || email == null) {
            NotificationRecipient stored = recipients.findById(orderId).orElse(null);
            if (stored != null) {
                if (customerId == null) {
                    customerId = stored.getCustomerId();
                }
                if (email == null) {
                    email = stored.getEmail();
                }
            }
        }
        return new Recipient(customerId, email);
    }

    private static UUID uuid(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Recipient(UUID customerId, String email) {
    }
}
