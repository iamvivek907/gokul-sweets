ALTER TABLE occasion_enquiries
    ADD COLUMN pickup_slot_id BIGINT REFERENCES pickup_slots(id),
    ADD COLUMN hold_expires_at TIMESTAMPTZ,
    ADD COLUMN balance_due_at TIMESTAMPTZ,
    ADD COLUMN confirmed_at TIMESTAMPTZ;

CREATE TABLE occasion_hold_items (
    enquiry_id UUID NOT NULL REFERENCES occasion_enquiries(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    reservation_key VARCHAR(100) NOT NULL UNIQUE REFERENCES inventory_reservations(reservation_key),
    PRIMARY KEY (enquiry_id, product_id)
);

CREATE TABLE occasion_payment_attempts (
    id UUID PRIMARY KEY,
    enquiry_id UUID NOT NULL REFERENCES occasion_enquiries(id),
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    stage VARCHAR(12) NOT NULL CHECK (stage IN ('DEPOSIT', 'BALANCE')),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REFUND_PENDING')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    merchant_order_id VARCHAR(100) NOT NULL UNIQUE,
    provider_order_id VARCHAR(150),
    provider_checkout_url TEXT,
    provider_transaction_id VARCHAR(150),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_occasion_payment_one_pending
    ON occasion_payment_attempts(enquiry_id, stage) WHERE status = 'PENDING';
CREATE INDEX idx_occasion_payment_enquiry ON occasion_payment_attempts(enquiry_id, created_at DESC);
CREATE INDEX idx_occasion_hold_expiry ON occasion_enquiries(hold_expires_at)
    WHERE status IN ('HELD', 'PAYMENT_PENDING');
