-- Existing orders are pickup orders. Delivery rows become possible only after the
-- order/payment lifecycle is wired; no public creation route uses this shape yet.
ALTER TABLE orders ADD COLUMN fulfillment_type VARCHAR(12) NOT NULL DEFAULT 'PICKUP';
ALTER TABLE orders ADD COLUMN delivery_window_id BIGINT REFERENCES delivery_capacity_windows(id);
ALTER TABLE orders ADD COLUMN delivery_hold_key VARCHAR(100) UNIQUE REFERENCES delivery_rider_holds(hold_key);
ALTER TABLE orders ADD COLUMN delivery_address_line VARCHAR(300);
ALTER TABLE orders ADD COLUMN delivery_locality VARCHAR(120);
ALTER TABLE orders ADD COLUMN delivery_postal_code CHAR(6);
ALTER TABLE delivery_rider_holds ADD CONSTRAINT uk_delivery_hold_key_window UNIQUE (hold_key, window_id);
ALTER TABLE orders ADD CONSTRAINT fk_orders_delivery_hold_window FOREIGN KEY (delivery_hold_key, delivery_window_id)
    REFERENCES delivery_rider_holds (hold_key, window_id);
ALTER TABLE orders ALTER COLUMN pickup_slot_id DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN pickup_type DROP NOT NULL;
ALTER TABLE orders ADD CONSTRAINT chk_orders_fulfillment_shape CHECK (
    (fulfillment_type = 'PICKUP' AND pickup_slot_id IS NOT NULL AND pickup_type IS NOT NULL
        AND delivery_window_id IS NULL AND delivery_hold_key IS NULL AND delivery_address_line IS NULL
        AND delivery_locality IS NULL AND delivery_postal_code IS NULL)
    OR
    (fulfillment_type = 'DELIVERY' AND pickup_slot_id IS NULL AND pickup_type IS NULL
        AND delivery_window_id IS NOT NULL AND delivery_hold_key IS NOT NULL
        AND NULLIF(BTRIM(delivery_address_line), '') IS NOT NULL
        AND NULLIF(BTRIM(delivery_locality), '') IS NOT NULL
        AND delivery_postal_code IS NOT NULL AND delivery_postal_code ~ '^[0-9]{6}$'
        AND priority_charge = 0 AND admin_override = FALSE)
);
CREATE INDEX idx_orders_delivery_window_id ON orders (delivery_window_id)
    WHERE delivery_window_id IS NOT NULL;
