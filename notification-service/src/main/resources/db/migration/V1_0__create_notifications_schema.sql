CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    event_id     UUID          NOT NULL,
    event_type   VARCHAR(64)   NOT NULL,
    order_id     UUID,
    customer_id  UUID,
    recipient    VARCHAR(320),
    channel      VARCHAR(16)   NOT NULL,
    subject      VARCHAR(200)  NOT NULL,
    body         TEXT          NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    error        VARCHAR(512),
    created_at   TIMESTAMPTZ   NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_notifications_event ON notifications (event_id);
CREATE INDEX idx_notifications_customer ON notifications (customer_id, created_at DESC);
CREATE INDEX idx_notifications_order ON notifications (order_id);
CREATE INDEX idx_notifications_status ON notifications (status);

CREATE TABLE notification_recipients (
    order_id    UUID PRIMARY KEY,
    customer_id UUID,
    email       VARCHAR(320),
    updated_at  TIMESTAMPTZ NOT NULL
);

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
