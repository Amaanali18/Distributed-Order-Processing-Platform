package com.dpp.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dpp.common.event.Topics;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String CLAIM_SQL = """
            SELECT id, topic, message_key, payload, attempts
            FROM outbox_messages
            WHERE status = 'PENDING' AND available_at <= :now
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final int maxAttempts;
    private final long retryBackoffMs;
    private final long sendTimeoutMs;

    public OutboxRelay(NamedParameterJdbcTemplate jdbc,
                       KafkaTemplate<Object, Object> kafkaTemplate,
                       TransactionTemplate transactions,
                       int batchSize,
                       int maxAttempts,
                       long retryBackoffMs,
                       long sendTimeoutMs) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.transactions = transactions;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryBackoffMs = retryBackoffMs;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${dpp.outbox.poll-interval-ms:500}",
            initialDelayString = "${dpp.outbox.initial-delay-ms:2000}")
    public void publishPending() {
        Integer published = transactions.execute(status -> publishBatch());
        if (published != null && published > 0) {
            log.debug("Published {} outbox message(s)", published);
        }
    }

    private Integer publishBatch() {
        List<OutboxMessage> messages = jdbc.query(CLAIM_SQL,
                Map.of("now", OffsetDateTime.now(ZoneOffset.UTC), "batchSize", batchSize),
                new OutboxMessageMapper());
        if (messages.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (OutboxMessage message : messages) {
            if (publish(message)) {
                markSent(message.id());
                published++;
            } else {
                handleFailedAttempt(message);
            }
        }
        return published;
    }

    private boolean publish(OutboxMessage message) {
        try {
            kafkaTemplate.send(message.topic(), message.messageKey(), message.payload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Outbox publish interrupted for message {}", message.id());
            return false;
        } catch (Exception e) {
            log.warn("Outbox publish failed for message {} (attempt {}): {}",
                    message.id(), message.attempts() + 1, e.getMessage());
            return false;
        }
    }

    private void markSent(UUID id) {
        jdbc.update("""
                UPDATE outbox_messages
                SET status = 'SENT', published_at = :now
                WHERE id = :id
                """, Map.of("id", id, "now", OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private void handleFailedAttempt(OutboxMessage message) {
        int attempts = message.attempts() + 1;
        if (attempts >= maxAttempts) {
            if (publishToDeadLetter(message)) {
                jdbc.update("""
                        UPDATE outbox_messages
                        SET status = 'DEAD_LETTERED',
                            attempts = :attempts,
                            last_error = :lastError
                        WHERE id = :id
                        """, Map.of(
                        "id", message.id(),
                        "attempts", attempts,
                        "lastError", "exhausted " + attempts + " attempts; routed to " + Topics.deadLetter(message.topic())));
                log.error("Outbox message {} exhausted {} attempts and was dead-lettered to {}",
                        message.id(), attempts, Topics.deadLetter(message.topic()));
                return;
            }
            jdbc.update("""
                    UPDATE outbox_messages
                    SET status = 'PENDING',
                        attempts = :attempts,
                        available_at = :availableAt,
                        last_error = :lastError
                    WHERE id = :id
                    """, Map.of(
                    "id", message.id(),
                    "attempts", attempts,
                    "availableAt", OffsetDateTime.ofInstant(
                            Instant.now().plusMillis(retryBackoffMs * Math.max(1, attempts)), ZoneOffset.UTC),
                    "lastError", "exhausted attempts; dead-letter send pending"));
            log.error("Outbox message {} exhausted {} attempts but dead-letter publish failed; will retry",
                    message.id(), attempts);
            return;
        }

        jdbc.update("""
                UPDATE outbox_messages
                SET status = 'PENDING',
                    attempts = :attempts,
                    available_at = :availableAt,
                    last_error = :lastError
                WHERE id = :id
                """, Map.of(
                "id", message.id(),
                "attempts", attempts,
                "availableAt", OffsetDateTime.ofInstant(
                        Instant.now().plusMillis(retryBackoffMs * attempts), ZoneOffset.UTC),
                "lastError", "publish attempt " + attempts + " failed"));
    }

    private boolean publishToDeadLetter(OutboxMessage message) {
        try {
            kafkaTemplate.send(Topics.deadLetter(message.topic()), message.messageKey(), message.payload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Dead-letter publish failed for outbox message {}: {}", message.id(), e.getMessage());
            return false;
        }
    }

    private record OutboxMessage(UUID id, String topic, String messageKey, String payload, int attempts) {
    }

    private static final class OutboxMessageMapper implements RowMapper<OutboxMessage> {

        @Override
        public OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OutboxMessage(
                    rs.getObject("id", UUID.class),
                    rs.getString("topic"),
                    rs.getString("message_key"),
                    rs.getString("payload"),
                    rs.getInt("attempts"));
        }
    }
}
