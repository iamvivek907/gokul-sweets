-- Opt in per branch; existing explicit selections retain their saved behavior.
ALTER TABLE rolling_branch_schedules
    ADD COLUMN use_policy_maximum BOOLEAN NOT NULL DEFAULT FALSE;
