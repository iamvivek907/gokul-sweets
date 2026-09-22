CREATE TABLE branch_pickup_settings (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,

    slot_duration_minutes INTEGER NOT NULL DEFAULT 30,
    default_capacity INTEGER NOT NULL DEFAULT 10,

    advance_booking_days INTEGER NOT NULL DEFAULT 7,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_branch_pickup_settings_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT uk_branch_pickup_settings_branch
        UNIQUE (branch_id),

    CONSTRAINT chk_pickup_settings_slot_duration
        CHECK (slot_duration_minutes > 0),

    CONSTRAINT chk_pickup_settings_capacity
        CHECK (default_capacity > 0),

    CONSTRAINT chk_pickup_settings_advance_days
        CHECK (advance_booking_days >= 0)
);

CREATE INDEX idx_branch_pickup_settings_branch
    ON branch_pickup_settings(branch_id);