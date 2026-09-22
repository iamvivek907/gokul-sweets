CREATE TABLE payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_webhook_provider_event
        UNIQUE (provider, provider_event_id),

    CONSTRAINT ck_payment_webhook_provider
        CHECK (provider IN ('PAYTM', 'RAZORPAY'))
);

CREATE INDEX idx_payment_webhook_created_at
    ON payment_webhook_events(created_at);

CREATE UNIQUE INDEX uq_payments_provider_order
    ON payments(provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

CREATE UNIQUE INDEX uq_payments_provider_payment
    ON payments(provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
