ALTER TABLE branch_pickup_settings
    ADD COLUMN priority_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE branch_pickup_settings
    ADD COLUMN default_priority_capacity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE branch_pickup_settings
    ADD COLUMN default_priority_charge DECIMAL(10, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE branch_pickup_settings
    ADD CONSTRAINT ck_branch_pickup_settings_priority_capacity
        CHECK (default_priority_capacity >= 0);

ALTER TABLE branch_pickup_settings
    ADD CONSTRAINT ck_branch_pickup_settings_priority_charge
        CHECK (default_priority_charge >= 0);