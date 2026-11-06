ALTER TABLE rolling_branch_schedules ADD COLUMN enabled_since_at TIMESTAMPTZ;
-- Earlier plans have no separate activation timestamp; preserve the last known saved anchor once.
UPDATE rolling_branch_schedules SET enabled_since_at=updated_at WHERE enabled;

-- Enforce activation semantics for both new and older application versions during deployment.
CREATE FUNCTION track_rolling_activation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.enabled AND (TG_OP='INSERT' OR NOT OLD.enabled) THEN
        NEW.enabled_since_at := clock_timestamp();
    ELSIF TG_OP='UPDATE' THEN
        NEW.enabled_since_at := OLD.enabled_since_at;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER rolling_activation_anchor
BEFORE INSERT OR UPDATE OF enabled ON rolling_branch_schedules
FOR EACH ROW EXECUTE FUNCTION track_rolling_activation();

ALTER TABLE rolling_branch_schedules ADD CONSTRAINT rolling_enabled_since_required
    CHECK (NOT enabled OR enabled_since_at IS NOT NULL);
