CREATE INDEX idx_orders_customer_created
    ON orders (customer_id, created_at DESC);

CREATE INDEX idx_orders_status_created
    ON orders (status, created_at DESC);
