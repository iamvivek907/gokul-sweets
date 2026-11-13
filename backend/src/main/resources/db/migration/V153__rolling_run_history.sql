CREATE TABLE rolling_automation_runs (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    trigger_type VARCHAR(12) NOT NULL CHECK(trigger_type IN ('SCHEDULED','MANUAL')),
    actor VARCHAR(150) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK(status IN ('RUNNING','SUCCESS','FAILED','SKIPPED')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT NOT NULL DEFAULT 0 CHECK(duration_ms >= 0),
    lock_wait_ms BIGINT NOT NULL DEFAULT 0 CHECK(lock_wait_ms >= 0),
    lock_held_ms BIGINT NOT NULL DEFAULT 0 CHECK(lock_held_ms >= 0),
    slots_created INTEGER NOT NULL DEFAULT 0,
    allocations_created INTEGER NOT NULL DEFAULT 0,
    summary VARCHAR(2000) NOT NULL DEFAULT ''
);
CREATE INDEX rolling_runs_branch_recent ON rolling_automation_runs(branch_id,started_at DESC,id DESC);

-- Allow durable run records to reference the branch while schedule writers remain serialized.
CREATE OR REPLACE FUNCTION protect_rolling_slot_overlap() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.occasion_enquiry_id IS NOT NULL THEN RETURN NEW; END IF;
    PERFORM id FROM branches WHERE id=NEW.branch_id FOR NO KEY UPDATE;
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
