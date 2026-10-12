ALTER TABLE payments ADD COLUMN refund_amount NUMERIC(12,2);
ALTER TABLE payments ADD CONSTRAINT payment_refund_amount_range CHECK (refund_amount IS NULL OR (refund_amount >= 0 AND refund_amount <= amount));
CREATE TABLE order_corrections (
 id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id), request_key UUID NOT NULL,
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('CANCEL','TRANSFER')), source_branch_id BIGINT NOT NULL REFERENCES branches(id),
 target_branch_id BIGINT REFERENCES branches(id), target_slot_id BIGINT REFERENCES pickup_slots(id),
 actor VARCHAR(150) NOT NULL, reason VARCHAR(500) NOT NULL, refund_amount NUMERIC(12,2), retained_charges NUMERIC(12,2),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(order_id,request_key)
);
CREATE UNIQUE INDEX order_one_cancellation ON order_corrections(order_id) WHERE kind='CANCEL';
CREATE TABLE rebate_visit_rules (
 rebate_id BIGINT PRIMARY KEY REFERENCES rebates(id) ON DELETE CASCADE,
 minimum_completed_orders INTEGER NOT NULL CHECK(minimum_completed_orders BETWEEN 0 AND 100000),
 updated_by BIGINT NOT NULL REFERENCES staff_users(id), reason VARCHAR(500) NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE rebate_visit_rule_audit (
 id BIGSERIAL PRIMARY KEY, rebate_id BIGINT NOT NULL REFERENCES rebates(id),minimum_completed_orders INTEGER NOT NULL,
 actor BIGINT NOT NULL REFERENCES staff_users(id),reason VARCHAR(500) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
