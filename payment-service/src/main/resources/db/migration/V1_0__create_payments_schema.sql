CREATE TABLE payment_attempts (
    id                  UUID PRIMARY KEY,
    order_id            UUID           NOT NULL,
    customer_id         UUID           NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    provider            VARCHAR(32)    NOT NULL,
    provider_payment_id VARCHAR(128),
    idempotency_key     VARCHAR(128)   NOT NULL UNIQUE,
    failure_code        VARCHAR(64),
    failure_message     VARCHAR(512),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_payment_attempts_order ON payment_attempts (order_id);

CREATE TABLE refunds (
    id                 UUID PRIMARY KEY,
    payment_id         UUID           NOT NULL REFERENCES payment_attempts (id) ON DELETE CASCADE,
    order_id           UUID           NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    provider_refund_id VARCHAR(128),
    reason             VARCHAR(256),
    created_at         TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);

CREATE TABLE outbox_messages (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    message_key  VARCHAR(128) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ  NOT NULL,
    last_error   VARCHAR(512),
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_messages (status, available_at, created_at);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
