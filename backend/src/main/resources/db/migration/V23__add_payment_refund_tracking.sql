ALTER TABLE payments
    ADD COLUMN refund_reference_id VARCHAR(50),
    ADD COLUMN provider_refund_id VARCHAR(150),
    ADD COLUMN refund_failure_reason VARCHAR(500),
    ADD COLUMN refund_requested_at TIMESTAMP,
    ADD COLUMN refund_last_checked_at TIMESTAMP,
    ADD COLUMN refunded_at TIMESTAMP;

CREATE UNIQUE INDEX uq_payments_refund_reference_id
    ON payments (refund_reference_id)
    WHERE refund_reference_id IS NOT NULL;

CREATE INDEX idx_payments_provider_refund_id
    ON payments (provider_refund_id);

CREATE INDEX idx_payments_refund_pending_updated
    ON payments (payment_status, updated_at);
