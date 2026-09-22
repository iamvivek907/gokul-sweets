CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL,

    provider VARCHAR(50) NOT NULL,

    provider_payment_id VARCHAR(150),

    provider_order_id VARCHAR(150),

    amount DECIMAL(12, 2) NOT NULL,

    currency VARCHAR(10) NOT NULL DEFAULT 'INR',

    payment_status VARCHAR(40) NOT NULL,

    failure_reason VARCHAR(500),

    paid_at TIMESTAMP,

    expires_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT chk_payments_amount
        CHECK (amount >= 0)
);

CREATE INDEX idx_payments_order_id
    ON payments(order_id);

CREATE INDEX idx_payments_status
    ON payments(payment_status);

CREATE INDEX idx_payments_provider_payment_id
    ON payments(provider_payment_id);

CREATE INDEX idx_payments_provider_order_id
    ON payments(provider_order_id);

CREATE INDEX idx_payments_created_at
    ON payments(created_at);