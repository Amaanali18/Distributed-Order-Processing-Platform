package com.dpp.notification;

import com.dpp.common.event.EventTypes;
import com.dpp.messaging.ProcessedEventStore;
import com.dpp.notification.domain.Notification;
import com.dpp.notification.repository.NotificationRecipientRepository;
import com.dpp.notification.repository.NotificationRepository;
import com.dpp.notification.sender.NotificationSender;
import com.dpp.notification.service.NotificationRenderer;
import com.dpp.notification.service.NotificationService;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000"
})
class NotificationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    NotificationService notifications;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    NotificationRecipientRepository recipientRepository;

    @Autowired
    ProcessedEventStore processedEvents;

    @Autowired
    NotificationRenderer renderer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private ObjectNode orderPayload(UUID orderId, UUID customerId, String email) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("customerEmail", email);
        payload.put("totalAmount", new BigDecimal("19.98"));
        payload.put("currency", "USD");
        return payload;
    }

    private ObjectNode paymentPayload(UUID orderId, UUID paymentId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("paymentId", paymentId.toString());
        payload.put("amount", new BigDecimal("19.98"));
        payload.put("currency", "USD");
        return payload;
    }

    private int notificationCount(UUID orderId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE order_id = ?",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private String notificationStatus(UUID eventId) {
        return jdbc.queryForObject("SELECT status FROM notifications WHERE event_id = ?", String.class, eventId);
    }

    @Test
    void orderCreatedRecordsSentNotificationAndRecipient() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        notifications.handle(EventTypes.ORDER_CREATED, eventId, orderId,
                orderPayload(orderId, customerId, "customer@dpp.local"), "corr-1");

        assertThat(notificationCount(orderId)).isEqualTo(1);
        assertThat(notificationStatus(eventId)).isEqualTo("SENT");
        assertThat(recipientRepository.findById(orderId)).get()
                .extracting("email").isEqualTo("customer@dpp.local");
    }

    @Test
    void duplicateEventIsIgnored() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        notifications.handle(EventTypes.ORDER_CREATED, eventId, orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");
        notifications.handle(EventTypes.ORDER_CREATED, eventId, orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        assertThat(notificationCount(orderId)).isEqualTo(1);
    }

    @Test
    void orderConfirmedRendersConfirmation() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        notifications.handle(EventTypes.ORDER_CREATED, UUID.randomUUID(), orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        notifications.handle(EventTypes.ORDER_CONFIRMED, eventId, orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        Notification stored = notificationRepository.findByEventId(eventId).orElseThrow();
        assertThat(stored.getSubject()).isEqualTo("Order confirmed");
        assertThat(stored.getStatus().name()).isEqualTo("SENT");
    }

    @Test
    void paymentEventResolvesRecipientFromReadModel() {
        UUID orderId = UUID.randomUUID();
        notifications.handle(EventTypes.ORDER_CREATED, UUID.randomUUID(), orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        UUID paymentEventId = UUID.randomUUID();
        notifications.handle(EventTypes.PAYMENT_SUCCEEDED, paymentEventId, orderId,
                paymentPayload(orderId, UUID.randomUUID()), "corr");

        Notification stored = notificationRepository.findByEventId(paymentEventId).orElseThrow();
        assertThat(stored.getRecipient()).isEqualTo("customer@dpp.local");
        assertThat(stored.getSubject()).isEqualTo("Payment received");
    }

    @Test
    void unhandledEventCreatesNothing() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        notifications.handle(EventTypes.INVENTORY_RESERVED, eventId, orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        assertThat(notificationCount(orderId)).isZero();
        Integer processed = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(processed).isZero();
    }

    @Test
    void failedDeliveryIsRecordedAsFailed() {
        NotificationService failing = new NotificationService(notificationRepository, recipientRepository,
                processedEvents, renderer, new NotificationSender() {
            @Override
            public String channel() {
                return "test";
            }

            @Override
            public void send(Notification notification) {
                throw new IllegalStateException("boom");
            }
        });

        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        failing.handle(EventTypes.ORDER_CREATED, eventId, orderId,
                orderPayload(orderId, UUID.randomUUID(), "customer@dpp.local"), "corr");

        Notification stored = notificationRepository.findByEventId(eventId).orElseThrow();
        assertThat(stored.getStatus().name()).isEqualTo("FAILED");
        assertThat(stored.getError()).contains("boom");
    }
}
