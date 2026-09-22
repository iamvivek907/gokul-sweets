CREATE TABLE pickup_slots (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,

    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    capacity INTEGER NOT NULL,
    booked_count INTEGER NOT NULL DEFAULT 0,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pickup_slots_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT chk_pickup_slots_capacity
        CHECK (capacity > 0),

    CONSTRAINT chk_pickup_slots_booked_count
        CHECK (booked_count >= 0),

    CONSTRAINT chk_pickup_slots_booked_capacity
        CHECK (booked_count <= capacity),

    CONSTRAINT chk_pickup_slots_time
        CHECK (end_time > start_time),

    CONSTRAINT uk_pickup_slots_branch_date_time
        UNIQUE (
            branch_id,
            slot_date,
            start_time,
            end_time
        )
);

CREATE INDEX idx_pickup_slots_branch_date
    ON pickup_slots(branch_id, slot_date);

CREATE INDEX idx_pickup_slots_branch_date_active
    ON pickup_slots(branch_id, slot_date, active);