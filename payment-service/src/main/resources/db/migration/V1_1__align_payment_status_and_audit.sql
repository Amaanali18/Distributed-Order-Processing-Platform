ALTER TABLE payment_attempts ADD COLUMN cancel_requested_at TIMESTAMPTZ;

UPDATE payment_attempts SET status = 'SUCCESS' WHERE status = 'SUCCEEDED';

ALTER TABLE refunds ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_payment_attempts_customer_created
    ON payment_attempts (customer_id, created_at DESC);

CREATE INDEX idx_refunds_order ON refunds (order_id);

CREATE TABLE payment_attempt_transitions (
    id          UUID PRIMARY KEY,
    payment_id  UUID        NOT NULL REFERENCES payment_attempts (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_transitions_payment
    ON payment_attempt_transitions (payment_id, created_at);
