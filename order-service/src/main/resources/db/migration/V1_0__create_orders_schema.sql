CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_id    UUID          NOT NULL,
    customer_email VARCHAR(320)  NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    total_amount   NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    currency       VARCHAR(3)    NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    order_id   UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID          NOT NULL,
    sku        VARCHAR(64)   NOT NULL,
    name       VARCHAR(160)  NOT NULL,
    quantity   INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
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
