package com.dpp.messaging;

import com.dpp.common.event.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OutboxWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String topic, String messageKey, EventEnvelope<?> envelope) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event " + envelope.eventId(), e);
        }

        Instant now = Instant.now();
        Map<String, Object> params = new HashMap<>();
        params.put("id", envelope.eventId());
        params.put("topic", topic);
        params.put("messageKey", messageKey);
        params.put("payload", payload);
        params.put("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));

        jdbc.update("""
                INSERT INTO outbox_messages (id, topic, message_key, payload, status, attempts, available_at, created_at)
                VALUES (:id, :topic, :messageKey, :payload, 'PENDING', 0, :now, :now)
                """, params);
    }
}
