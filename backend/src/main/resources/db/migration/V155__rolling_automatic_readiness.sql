-- The rolling automation is a branch guarantee. Apply readiness as part of its audited
-- automatic approval path; product policies and manual inventory remain unchanged.
ALTER TABLE rolling_branch_schedules ADD COLUMN auto_ready BOOLEAN NOT NULL DEFAULT TRUE;
