CREATE TABLE rebates (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(50) NOT NULL UNIQUE,

    name VARCHAR(150) NOT NULL,

    description VARCHAR(500),

    scope VARCHAR(30) NOT NULL,

    rebate_type VARCHAR(30) NOT NULL,

    rebate_value DECIMAL(12,2),

    minimum_order_amount DECIMAL(12,2),

    maximum_discount_amount DECIMAL(12,2),

    max_total_uses INTEGER,

    max_uses_per_customer INTEGER,

    branch_id BIGINT,

    valid_from TIMESTAMP NOT NULL,

    valid_until TIMESTAMP NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_by BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rebates_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT fk_rebates_created_by
        FOREIGN KEY (created_by)
        REFERENCES staff_users(id),

    CONSTRAINT chk_rebate_value
        CHECK (
            rebate_value IS NULL
            OR rebate_value > 0
        ),

    CONSTRAINT chk_rebate_minimum_order
        CHECK (
            minimum_order_amount IS NULL
            OR minimum_order_amount >= 0
        ),

    CONSTRAINT chk_rebate_max_discount
        CHECK (
            maximum_discount_amount IS NULL
            OR maximum_discount_amount >= 0
        ),

    CONSTRAINT chk_rebate_total_uses
        CHECK (
            max_total_uses IS NULL
            OR max_total_uses > 0
        ),

    CONSTRAINT chk_rebate_customer_uses
        CHECK (
            max_uses_per_customer IS NULL
            OR max_uses_per_customer > 0
        ),

    CONSTRAINT chk_rebate_validity
        CHECK (
            valid_until > valid_from
        )
);

CREATE INDEX idx_rebates_active_validity
    ON rebates(
        active,
        valid_from,
        valid_until
    );

CREATE INDEX idx_rebates_branch
    ON rebates(branch_id);

CREATE INDEX idx_rebates_scope
    ON rebates(scope);


CREATE TABLE rebate_slabs (
    id BIGSERIAL PRIMARY KEY,

    rebate_id BIGINT NOT NULL,

    minimum_order_amount DECIMAL(12,2) NOT NULL,

    rebate_amount DECIMAL(12,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rebate_slabs_rebate
        FOREIGN KEY (rebate_id)
        REFERENCES rebates(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_rebate_slab_threshold
        UNIQUE (
            rebate_id,
            minimum_order_amount
        ),

    CONSTRAINT chk_rebate_slab_minimum
        CHECK (
            minimum_order_amount > 0
        ),

    CONSTRAINT chk_rebate_slab_amount
        CHECK (
            rebate_amount > 0
        )
);

CREATE INDEX idx_rebate_slabs_rebate
    ON rebate_slabs(
        rebate_id,
        minimum_order_amount
    );


CREATE TABLE rebate_customers (
    id BIGSERIAL PRIMARY KEY,

    rebate_id BIGINT NOT NULL,

    customer_phone VARCHAR(20) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rebate_customers_rebate
        FOREIGN KEY (rebate_id)
        REFERENCES rebates(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_rebate_customer
        UNIQUE (
            rebate_id,
            customer_phone
        )
);

CREATE INDEX idx_rebate_customers_phone
    ON rebate_customers(customer_phone);


CREATE TABLE rebate_redemptions (
    id BIGSERIAL PRIMARY KEY,

    rebate_id BIGINT NOT NULL,

    order_id BIGINT NOT NULL UNIQUE,

    customer_phone VARCHAR(20) NOT NULL,

    discount_amount DECIMAL(12,2) NOT NULL,

    redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rebate_redemptions_rebate
        FOREIGN KEY (rebate_id)
        REFERENCES rebates(id),

    CONSTRAINT fk_rebate_redemptions_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT chk_rebate_redemption_amount
        CHECK (
            discount_amount >= 0
        )
);

CREATE INDEX idx_rebate_redemptions_rebate
    ON rebate_redemptions(rebate_id);

CREATE INDEX idx_rebate_redemptions_customer
    ON rebate_redemptions(
        rebate_id,
        customer_phone
    );


ALTER TABLE orders
    ADD COLUMN rebate_id BIGINT,

    ADD COLUMN rebate_code VARCHAR(50),

    ADD COLUMN rebate_discount_amount DECIMAL(12,2)
        NOT NULL DEFAULT 0,

    ADD CONSTRAINT fk_orders_rebate
        FOREIGN KEY (rebate_id)
        REFERENCES rebates(id),

    ADD CONSTRAINT chk_orders_rebate_discount
        CHECK (
            rebate_discount_amount >= 0
        );