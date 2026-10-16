CREATE TABLE products (
    id             UUID PRIMARY KEY,
    sku            VARCHAR(64)     NOT NULL UNIQUE,
    name           VARCHAR(160)    NOT NULL,
    description    VARCHAR(2000),
    price          NUMERIC(19, 2)  NOT NULL CHECK (price >= 0),
    currency       VARCHAR(3)      NOT NULL,
    status         VARCHAR(20)     NOT NULL,
    total_stock    INTEGER         NOT NULL CHECK (total_stock >= 0),
    reserved_stock INTEGER         NOT NULL CHECK (reserved_stock >= 0),
    version        BIGINT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ     NOT NULL,
    updated_at     TIMESTAMPTZ     NOT NULL,
    CONSTRAINT chk_stock CHECK (reserved_stock <= total_stock)
);

CREATE INDEX idx_products_status ON products (status);

CREATE TABLE reservations (
    id         UUID PRIMARY KEY,
    order_id   UUID       NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reservations_status ON reservations (status);

CREATE TABLE reservation_items (
    reservation_id UUID    NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    product_id     UUID    NOT NULL,
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, product_id)
);
