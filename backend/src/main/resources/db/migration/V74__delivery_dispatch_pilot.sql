CREATE TABLE delivery_pilot_riders (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(id, branch_id)
);
CREATE TABLE delivery_rider_availability (
    rider_id BIGINT NOT NULL REFERENCES delivery_pilot_riders(id),
    window_id BIGINT NOT NULL REFERENCES delivery_capacity_windows(id),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (rider_id, window_id)
);
CREATE TABLE delivery_pilot_assignments (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id),
    rider_id BIGINT NOT NULL REFERENCES delivery_pilot_riders(id),
    window_id BIGINT NOT NULL REFERENCES delivery_capacity_windows(id),
    state VARCHAR(25) NOT NULL CHECK (state IN ('ASSIGNED', 'DISPATCHED', 'DELIVERED', 'EXCEPTION')),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actual_journey_cost NUMERIC(12,2) CHECK (actual_journey_cost >= 0),
    outcome VARCHAR(120),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- One rider gets one trip per window until measured grouping is explicitly designed and approved.
CREATE UNIQUE INDEX uq_delivery_active_rider_window ON delivery_pilot_assignments(rider_id, window_id)
    WHERE state IN ('ASSIGNED', 'DISPATCHED');
CREATE TABLE delivery_dispatch_exceptions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('RIDER_NO_SHOW', 'BAD_ROADS', 'MISSING_ADDRESS',
        'FOOD_READY_EARLY', 'NO_SIGNAL', 'FAILED_HANDOFF', 'OTHER')),
    detail VARCHAR(300) NOT NULL,
    customer_contacted BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
