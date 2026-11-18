-- Durable webhook deduplication. Stripe retries events with the same id; the
-- primary key makes recording that fact atomic across payment-service instances.
CREATE TABLE processed_webhook_events (
    event_id     VARCHAR(128) PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);
