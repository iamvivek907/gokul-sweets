CREATE TABLE order_idempotency (
    id BIGSERIAL PRIMARY KEY,

    idempotency_key VARCHAR(100) NOT NULL,

    request_hash VARCHAR(64) NOT NULL,

    order_id BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_order_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT uk_order_idempotency_order
        UNIQUE (order_id),

    CONSTRAINT fk_order_idempotency_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
);

CREATE INDEX idx_order_idempotency_created_at
    ON order_idempotency(created_at);