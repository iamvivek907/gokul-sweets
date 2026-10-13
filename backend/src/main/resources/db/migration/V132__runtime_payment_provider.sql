-- One routing decision for NEW attempts; existing payments retain payments.provider.
CREATE TABLE payment_provider_routing (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    active_provider VARCHAR(16) CHECK (active_provider IN ('PHONEPE', 'RAZORPAY')),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    changed_at TIMESTAMPTZ
);
INSERT INTO payment_provider_routing(singleton) VALUES (TRUE);
CREATE TABLE payment_provider_switch_audit (
    id BIGSERIAL PRIMARY KEY,
    previous_provider VARCHAR(16) NOT NULL,
    active_provider VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL UNIQUE,
    staff_id BIGINT REFERENCES staff_users(id) ON DELETE SET NULL,
    staff_username VARCHAR(100) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
