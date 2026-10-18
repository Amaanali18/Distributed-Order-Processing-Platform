package com.dpp.messaging;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public class ProcessedEventStore {

    private final NamedParameterJdbcTemplate jdbc;

    public ProcessedEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean firstTime(UUID eventId, String eventType) {
        int inserted = jdbc.update("""
                INSERT INTO processed_events (event_id, event_type, processed_at)
                VALUES (:eventId, :eventType, :processedAt)
                ON CONFLICT (event_id) DO NOTHING
                """, Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "processedAt", OffsetDateTime.now(ZoneOffset.UTC)));
        return inserted > 0;
    }
}
