ALTER TABLE orders
    ADD COLUMN reservation_expires_at TIMESTAMP;

UPDATE orders
SET reservation_expires_at =
        created_at + INTERVAL '15 minutes'
WHERE reservation_expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN reservation_expires_at SET NOT NULL;

CREATE INDEX idx_orders_reservation_expiry
    ON orders (
        order_status,
        reservation_expires_at
    );