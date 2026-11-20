CREATE INDEX idx_reservations_status_expires
    ON reservations (status, expires_at);
