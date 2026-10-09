-- Preserve the last accepted failure acknowledgement after the active claim is cleared.
ALTER TABLE print_jobs ADD COLUMN failed_claim_token VARCHAR(100);
ALTER TABLE print_jobs ADD COLUMN failed_claim_agent VARCHAR(120);
