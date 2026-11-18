package com.dpp.payment.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Claims provider webhook event ids before domain handling. A claim is upgraded
 * to PROCESSED only after handling succeeds; a failed attempt is marked FAILED
 * so the provider's redelivery can claim it again. Atomic INSERT/UPDATE avoids
 * read-then-write races across payment-service instances.
 */
@Repository
public class WebhookEventStore {

    public enum Claim { NEW, DUPLICATE, IN_PROGRESS }

    private static final String SELECT_STATUS =
            "SELECT status FROM processed_webhook_events WHERE event_id = :eventId";

    private final NamedParameterJdbcTemplate jdbc;

    public WebhookEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Claim claim(String eventId, String eventType) {
        MapSqlParameterSource params = params(eventId, eventType);
        int inserted = jdbc.update("""
                        INSERT INTO processed_webhook_events (event_id, event_type, processed_at, status)
                        VALUES (:eventId, :eventType, :processedAt, 'PROCESSING')
                        ON CONFLICT (event_id) DO NOTHING
                        """, params);
        if (inserted == 1) {
            return Claim.NEW;
        }

        // Another delivery is either handled, currently running, or failed.
        for (int attempt = 0; attempt < 3; attempt++) {
            String status = jdbc.queryForObject(SELECT_STATUS, params, String.class);
            if ("PROCESSED".equals(status)) {
                return Claim.DUPLICATE;
            }
            if ("PROCESSING".equals(status)) {
                return Claim.IN_PROGRESS;
            }
            if ("FAILED".equals(status)) {
                int reclaimed = jdbc.update("""
                                UPDATE processed_webhook_events
                                SET status = 'PROCESSING', processed_at = :processedAt
                                WHERE event_id = :eventId AND status = 'FAILED'
                                """, params);
                if (reclaimed == 1) {
                    return Claim.NEW;
                }
            }
        }
        return Claim.IN_PROGRESS;
    }

    public void markProcessed(String eventId) {
        jdbc.update("""
                UPDATE processed_webhook_events
                SET status = 'PROCESSED', processed_at = :processedAt
                WHERE event_id = :eventId
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("processedAt", Timestamp.from(Instant.now())));
    }

    public void markFailed(String eventId) {
        jdbc.update("""
                UPDATE processed_webhook_events
                SET status = 'FAILED', processed_at = :processedAt
                WHERE event_id = :eventId AND status = 'PROCESSING'
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("processedAt", Timestamp.from(Instant.now())));
    }

    private MapSqlParameterSource params(String eventId, String eventType) {
        return new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("eventType", eventType == null ? "" : eventType)
                .addValue("processedAt", Timestamp.from(Instant.now()));
    }
}
