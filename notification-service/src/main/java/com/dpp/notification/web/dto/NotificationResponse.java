package com.dpp.notification.web.dto;

import com.dpp.notification.domain.Notification;
import com.dpp.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID customerId,
        String recipient,
        String channel,
        String subject,
        String body,
        NotificationStatus status,
        String error,
        Instant createdAt,
        Instant deliveredAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventId(),
                notification.getEventType(),
                notification.getOrderId(),
                notification.getCustomerId(),
                notification.getRecipient(),
                notification.getChannel(),
                notification.getSubject(),
                notification.getBody(),
                notification.getStatus(),
                notification.getError(),
                notification.getCreatedAt(),
                notification.getDeliveredAt());
    }
}
