package com.dpp.order;

import com.dpp.messaging.OutboxRelay;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000",
        "dpp.outbox.poll-interval-ms=3600000",
        "dpp.outbox.max-attempts=1"
})
class OutboxRelayIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    KafkaTemplate<Object, Object> kafkaTemplate;

    private UUID outboxId;

    @BeforeEach
    void insertPendingMessage() {
        outboxId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_messages (id, topic, message_key, payload, status, attempts, available_at, created_at)
                VALUES (?, 'orders.v1', ?, '{"eventType":"OrderCreated"}', 'PENDING', 0, ?, ?)
                """, outboxId, UUID.randomUUID().toString(),
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM outbox_messages WHERE id = ?", String.class, id);
    }

    private int attemptsOf(UUID id) {
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM outbox_messages WHERE id = ?", Integer.class, id);
        return attempts == null ? 0 : attempts;
    }

    @Test
    void exhaustedMessageIsRoutedToDeadLetterTopic() {
        CompletableFuture<SendResult<Object, Object>> failure =
                CompletableFuture.failedFuture(new RuntimeException("kafka down"));
        CompletableFuture<SendResult<Object, Object>> success = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("orders.v1"), any(), any())).thenReturn(failure);
        when(kafkaTemplate.send(eq("orders.v1.DLQ"), any(), any())).thenReturn(success);

        relay.publishPending();

        assertThat(statusOf(outboxId)).isEqualTo("DEAD_LETTERED");
        assertThat(attemptsOf(outboxId)).isEqualTo(1);
    }

    @Test
    void messageStaysPendingWhenDeadLetterAlsoFails() {
        CompletableFuture<SendResult<Object, Object>> failure =
                CompletableFuture.failedFuture(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failure);

        relay.publishPending();

        assertThat(statusOf(outboxId)).isEqualTo("PENDING");
        assertThat(attemptsOf(outboxId)).isEqualTo(1);
    }
}
