CREATE TABLE branch_inventory_policies (
    id BIGSERIAL PRIMARY KEY,
    branch_product_id BIGINT NOT NULL,
    control_mode VARCHAR(30) NOT NULL,
    inventory_unit VARCHAR(20) NOT NULL,
    online_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ready_stock_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_safety_buffer NUMERIC(14, 3) NOT NULL DEFAULT 0,
    maximum_daily_allocation NUMERIC(14, 3),
    booking_horizon_days INTEGER NOT NULL DEFAULT 14,
    production_lead_minutes INTEGER NOT NULL DEFAULT 0,
    shelf_life_minutes INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_inventory_policy_branch_product
        FOREIGN KEY (branch_product_id)
        REFERENCES branch_products(id),

    CONSTRAINT uk_inventory_policy_branch_product
        UNIQUE (branch_product_id),

    CONSTRAINT ck_inventory_policy_control_mode
        CHECK (control_mode IN (
            'READY_STOCK',
            'DAILY_PRODUCTION',
            'SLOT_CAPACITY',
            'MANUAL'
        )),

    CONSTRAINT ck_inventory_policy_unit
        CHECK (inventory_unit IN (
            'PIECE',
            'GRAM',
            'CAPACITY_POINT'
        )),

    CONSTRAINT ck_inventory_policy_buffer
        CHECK (default_safety_buffer >= 0),

    CONSTRAINT ck_inventory_policy_maximum
        CHECK (
            maximum_daily_allocation IS NULL
            OR maximum_daily_allocation > 0
        ),

    CONSTRAINT ck_inventory_policy_horizon
        CHECK (booking_horizon_days BETWEEN 0 AND 365),

    CONSTRAINT ck_inventory_policy_lead
        CHECK (production_lead_minutes >= 0),

    CONSTRAINT ck_inventory_policy_shelf_life
        CHECK (
            shelf_life_minutes IS NULL
            OR shelf_life_minutes > 0
        )
);


CREATE TABLE inventory_daily_allocations (
    id BIGSERIAL PRIMARY KEY,
    branch_product_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    inventory_unit VARCHAR(20) NOT NULL,
    approved_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    ready_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    safety_buffer_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    held_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    committed_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    fulfilled_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    wasted_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    forecast_quantity NUMERIC(14, 3),
    forecast_confidence VARCHAR(20),
    expected_ready_at TIMESTAMP,
    actual_ready_at TIMESTAMP,
    approved_by VARCHAR(150),
    approved_at TIMESTAMP,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_inventory_allocation_branch_product
        FOREIGN KEY (branch_product_id)
        REFERENCES branch_products(id),

    CONSTRAINT uk_inventory_allocation_product_date
        UNIQUE (branch_product_id, service_date),

    CONSTRAINT ck_inventory_allocation_status
        CHECK (status IN (
            'DRAFT',
            'APPROVED',
            'READY',
            'DELAYED',
            'UNAVAILABLE',
            'CLOSED'
        )),

    CONSTRAINT ck_inventory_allocation_unit
        CHECK (inventory_unit IN (
            'PIECE',
            'GRAM',
            'CAPACITY_POINT'
        )),

    CONSTRAINT ck_inventory_allocation_quantities
        CHECK (
            approved_quantity >= 0
            AND ready_quantity >= 0
            AND safety_buffer_quantity >= 0
            AND held_quantity >= 0
            AND committed_quantity >= 0
            AND fulfilled_quantity >= 0
            AND wasted_quantity >= 0
            AND (
                forecast_quantity IS NULL
                OR forecast_quantity >= 0
            )
        )
);


CREATE TABLE inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    allocation_id BIGINT NOT NULL,
    reservation_key VARCHAR(100) NOT NULL,
    order_number VARCHAR(50),
    quantity NUMERIC(14, 3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    released_at TIMESTAMP,
    release_reason VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_inventory_reservation_allocation
        FOREIGN KEY (allocation_id)
        REFERENCES inventory_daily_allocations(id),

    CONSTRAINT uk_inventory_reservation_key
        UNIQUE (reservation_key),

    CONSTRAINT ck_inventory_reservation_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_inventory_reservation_status
        CHECK (status IN (
            'TEMPORARY_HOLD',
            'CONFIRMED',
            'RELEASED',
            'EXPIRED',
            'FULFILLED',
            'CANCELLED'
        ))
);


CREATE TABLE inventory_stock_transactions (
    id BIGSERIAL PRIMARY KEY,
    branch_product_id BIGINT NOT NULL,
    allocation_id BIGINT,
    reservation_id BIGINT,
    transaction_type VARCHAR(40) NOT NULL,
    quantity_delta NUMERIC(14, 3) NOT NULL,
    order_number VARCHAR(50),
    reference_key VARCHAR(100),
    reason VARCHAR(500),
    performed_by VARCHAR(150),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_inventory_transaction_branch_product
        FOREIGN KEY (branch_product_id)
        REFERENCES branch_products(id),

    CONSTRAINT fk_inventory_transaction_allocation
        FOREIGN KEY (allocation_id)
        REFERENCES inventory_daily_allocations(id),

    CONSTRAINT fk_inventory_transaction_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES inventory_reservations(id),

    CONSTRAINT ck_inventory_transaction_type
        CHECK (transaction_type IN (
            'ALLOCATION_APPROVED',
            'READY_STOCK_RECORDED',
            'TEMPORARY_HOLD',
            'HOLD_RELEASED',
            'COMMITMENT_CONFIRMED',
            'COMMITMENT_CANCELLED',
            'FULFILLED',
            'WASTAGE',
            'ADJUSTMENT',
            'CARRY_FORWARD'
        )),

    CONSTRAINT ck_inventory_transaction_delta
        CHECK (quantity_delta <> 0)
);


CREATE INDEX idx_inventory_policy_online_enabled
    ON branch_inventory_policies(online_enabled);

CREATE INDEX idx_inventory_allocation_branch_date
    ON inventory_daily_allocations(service_date, branch_product_id);

CREATE INDEX idx_inventory_allocation_status_date
    ON inventory_daily_allocations(status, service_date);

CREATE INDEX idx_inventory_reservation_expiry
    ON inventory_reservations(status, expires_at);

CREATE INDEX idx_inventory_reservation_order
    ON inventory_reservations(order_number);

CREATE INDEX idx_inventory_transaction_product_created
    ON inventory_stock_transactions(branch_product_id, created_at);

CREATE INDEX idx_inventory_transaction_order
    ON inventory_stock_transactions(order_number);

