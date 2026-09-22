CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,

    order_number VARCHAR(50) NOT NULL UNIQUE,

    branch_id BIGINT NOT NULL,

    pickup_slot_id BIGINT NOT NULL,

    customer_name VARCHAR(150) NOT NULL,

    customer_phone VARCHAR(20) NOT NULL,

    pickup_type VARCHAR(30) NOT NULL,

    priority_charge DECIMAL(10, 2) NOT NULL DEFAULT 0.00,

    subtotal DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    order_status VARCHAR(40) NOT NULL,

    admin_override BOOLEAN NOT NULL DEFAULT FALSE,

    override_reason VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT fk_orders_pickup_slot
        FOREIGN KEY (pickup_slot_id)
        REFERENCES pickup_slots(id),

    CONSTRAINT chk_orders_priority_charge
        CHECK (priority_charge >= 0),

    CONSTRAINT chk_orders_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_orders_tax_amount
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_orders_total_amount
        CHECK (total_amount >= 0),

    CONSTRAINT chk_orders_override_reason
        CHECK (
            admin_override = FALSE
            OR override_reason IS NOT NULL
        )
);

CREATE INDEX idx_orders_branch_id
    ON orders(branch_id);

CREATE INDEX idx_orders_pickup_slot_id
    ON orders(pickup_slot_id);

CREATE INDEX idx_orders_status
    ON orders(order_status);

CREATE INDEX idx_orders_branch_status
    ON orders(branch_id, order_status);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);

CREATE INDEX idx_orders_customer_phone
    ON orders(customer_phone);