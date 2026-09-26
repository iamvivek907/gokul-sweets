-- Identity ownership is recorded only at new checkout with a matching live session.
-- The subject UUID is intentionally not FK-bound: reverification rotates the current
-- subject while preserving the historical owner of earlier orders for audit/recovery.
CREATE TABLE verified_order_ownership (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    verified_subject_id UUID NOT NULL,
    bound_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_verified_order_ownership_subject
    ON verified_order_ownership(environment, verified_subject_id, bound_at DESC);
