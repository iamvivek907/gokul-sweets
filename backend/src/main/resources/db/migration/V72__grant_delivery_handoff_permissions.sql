-- Delivery dispatch and final receipt require distinct staff permissions.
INSERT INTO permissions (name, description) VALUES
    ('ORDER_DISPATCH_DELIVERY', 'Record a prepared order leaving the branch for delivery'),
    ('ORDER_CONFIRM_DELIVERY', 'Confirm that a dispatched order reached the customer')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('OWNER_ADMIN', 'MANAGER')
  AND p.name IN ('ORDER_DISPATCH_DELIVERY', 'ORDER_CONFIRM_DELIVERY')
ON CONFLICT DO NOTHING;

-- Even a direct database write cannot label delivery as collected at the counter.
ALTER TABLE orders ADD CONSTRAINT chk_orders_status_matches_fulfillment CHECK (
    (fulfillment_type = 'PICKUP'
        AND order_status NOT IN ('READY_FOR_DELIVERY', 'OUT_FOR_DELIVERY', 'DELIVERED'))
    OR (fulfillment_type = 'DELIVERY'
        AND order_status NOT IN ('READY_FOR_PICKUP', 'PICKED_UP', 'NO_SHOW', 'PICKUP_WINDOW_EXPIRED'))
);
