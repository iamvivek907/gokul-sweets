ALTER TABLE pickup_slots
ADD COLUMN priority_enabled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN priority_capacity INTEGER NOT NULL DEFAULT 0,
ADD COLUMN priority_booked_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN priority_charge DECIMAL(10, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE pickup_slots
ADD CONSTRAINT chk_pickup_slots_priority_capacity
CHECK (priority_capacity >= 0);

ALTER TABLE pickup_slots
ADD CONSTRAINT chk_pickup_slots_priority_booked_count
CHECK (priority_booked_count >= 0);

ALTER TABLE pickup_slots
ADD CONSTRAINT chk_pickup_slots_priority_booked_capacity
CHECK (priority_booked_count <= priority_capacity);

ALTER TABLE pickup_slots
ADD CONSTRAINT chk_pickup_slots_priority_charge
CHECK (priority_charge >= 0);