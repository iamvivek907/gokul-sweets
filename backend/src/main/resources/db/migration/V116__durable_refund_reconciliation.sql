ALTER TABLE payments
    ADD COLUMN refund_submission_attempted_at TIMESTAMP,
    ADD COLUMN refund_next_check_at TIMESTAMP,
    ADD COLUMN refund_check_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN refund_review_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Older pending refunds may already have reached the gateway. Never assume they are new.
UPDATE payments SET refund_submission_attempted_at = COALESCE(refund_requested_at, updated_at)
WHERE payment_status = 'REFUND_PENDING';

CREATE INDEX idx_payments_due_refunds ON payments (refund_next_check_at, id)
WHERE payment_status = 'REFUND_PENDING';
