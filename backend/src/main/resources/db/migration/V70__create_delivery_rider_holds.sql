CREATE TABLE delivery_rider_holds (
    hold_key VARCHAR(100) PRIMARY KEY,
    window_id BIGINT NOT NULL REFERENCES delivery_capacity_windows(id),
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(12) NOT NULL DEFAULT 'HELD',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_rider_hold_state CHECK (state IN ('HELD', 'COMMITTED', 'RELEASED')),
    CONSTRAINT chk_delivery_rider_hold_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_delivery_rider_holds_expiry ON delivery_rider_holds (expires_at) WHERE state = 'HELD';
