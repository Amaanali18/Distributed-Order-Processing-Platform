package com.dpp.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void ofPopulatesIdentityAndMetadata() {
        EventEnvelope<String> envelope = EventEnvelope.of(
                EventTypes.ORDER_CREATED, 1, "order-1", "corr-1", "order-service", "payload");

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.aggregateId()).isEqualTo("order-1");
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.producer()).isEqualTo("order-service");
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    @Test
    void eachEnvelopeGetsADistinctEventId() {
        EventEnvelope<String> first = EventEnvelope.of(
                EventTypes.PAYMENT_SUCCEEDED, 1, "order-1", null, "payment-service", "p");
        EventEnvelope<String> second = EventEnvelope.of(
                EventTypes.PAYMENT_SUCCEEDED, 1, "order-1", null, "payment-service", "p");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.eventId()).isNotEqualTo(new UUID(0, 0));
    }

    @Test
    void retryAndDeadLetterTopicsAreSuffixed() {
        assertThat(Topics.retry(Topics.ORDERS)).isEqualTo("orders.v1.retry");
        assertThat(Topics.deadLetter(Topics.PAYMENTS)).isEqualTo("payments.v1.DLQ");
    }
}
