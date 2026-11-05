CREATE TABLE rolling_branch_schedules (
    branch_id BIGINT PRIMARY KEY REFERENCES branches(id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    slot_minutes INTEGER NOT NULL CHECK (slot_minutes BETWEEN 5 AND 180),
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 10000),
    maintain_days INTEGER NOT NULL CHECK (maintain_days BETWEEN 1 AND 60),
    weekdays INTEGER NOT NULL CHECK (weekdays BETWEEN 1 AND 127),
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(150) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_message VARCHAR(2000),
    CHECK (closing_time > opening_time)
);
CREATE TABLE rolling_inventory_templates (
    branch_id BIGINT NOT NULL REFERENCES rolling_branch_schedules(branch_id),
    policy_id BIGINT NOT NULL REFERENCES branch_inventory_policies(id),
    quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(branch_id,policy_id)
);
CREATE TABLE rolling_schedule_dates (
    branch_id BIGINT NOT NULL REFERENCES rolling_branch_schedules(branch_id),
    service_date DATE NOT NULL,
    PRIMARY KEY(branch_id,service_date)
);
ALTER TABLE pickup_slots ADD COLUMN rolling_generated BOOLEAN NOT NULL DEFAULT FALSE;

-- Serialize slot creation with the scheduler without changing ordinary manual/manual overlap rules.
-- A manually disabled automatic slot is kept; automation never reactivates it.
CREATE FUNCTION protect_rolling_slot_overlap() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.occasion_enquiry_id IS NOT NULL THEN RETURN NEW; END IF;
    PERFORM id FROM branches WHERE id=NEW.branch_id FOR UPDATE;
    IF EXISTS (
        SELECT 1 FROM pickup_slots s WHERE s.branch_id=NEW.branch_id
        AND s.slot_date=NEW.slot_date AND s.occasion_enquiry_id IS NULL
        AND s.id<>COALESCE(NEW.id,-1)
        AND s.start_time<NEW.end_time AND s.end_time>NEW.start_time
        AND (NEW.rolling_generated OR s.rolling_generated)
    ) THEN
        IF NEW.rolling_generated THEN RETURN NULL; END IF;
        RAISE EXCEPTION 'This time overlaps an automated pickup slot. Edit the existing slot instead.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER rolling_slot_overlap_guard
BEFORE INSERT OR UPDATE OF branch_id,slot_date,start_time,end_time ON pickup_slots
FOR EACH ROW EXECUTE FUNCTION protect_rolling_slot_overlap();
