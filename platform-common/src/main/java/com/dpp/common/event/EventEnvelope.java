package com.dpp.common.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        String producer,
        T payload) {

    public static <T> EventEnvelope<T> of(String eventType,
                                          int schemaVersion,
                                          String aggregateId,
                                          String correlationId,
                                          String producer,
                                          T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                schemaVersion,
                aggregateId,
                Instant.now(),
                correlationId,
                producer,
                payload);
    }
}
