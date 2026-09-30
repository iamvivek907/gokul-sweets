ALTER TABLE occasion_enquiries ADD COLUMN estimated BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN original_estimate NUMERIC(12,2), ADD COLUMN quote_calculation JSONB,
 ADD COLUMN extra_charges JSONB NOT NULL DEFAULT '[]', ADD COLUMN packing_finalized_at TIMESTAMPTZ, ADD COLUMN estimate_accepted_at TIMESTAMPTZ,
 ADD COLUMN packing_revision INTEGER NOT NULL DEFAULT 0, ADD COLUMN credit_review_amount NUMERIC(12,2);
CREATE TABLE occasion_packing_finalizations (
 id BIGSERIAL PRIMARY KEY, enquiry_id UUID NOT NULL REFERENCES occasion_enquiries(id),
 revision INTEGER NOT NULL, original_estimate NUMERIC(12,2) NOT NULL, final_amount NUMERIC(12,2) NOT NULL,
 calculation JSONB NOT NULL, actor VARCHAR(150) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(enquiry_id,revision)
);

-- Preserve the advance ledger when the packed invoice is lower than the advance.
-- Only a finalized estimated booking with a recorded credit may exceed its invoice.
DO $$
DECLARE advance_constraint RECORD;
BEGIN
 FOR advance_constraint IN
  SELECT conname FROM pg_constraint
  WHERE conrelid = 'occasion_enquiries'::regclass AND contype = 'c'
   AND pg_get_constraintdef(oid) LIKE '%deposit_amount%'
   AND pg_get_constraintdef(oid) LIKE '%quoted_amount%'
 LOOP
  EXECUTE format('ALTER TABLE occasion_enquiries DROP CONSTRAINT %I', advance_constraint.conname);
 END LOOP;
END $$;
ALTER TABLE occasion_enquiries ADD CONSTRAINT occasion_advance_invoice_check CHECK (
 deposit_amount IS NULL OR (quoted_amount IS NOT NULL AND
  (deposit_amount <= quoted_amount OR
   (estimated AND packing_finalized_at IS NOT NULL AND credit_review_amount IS NOT NULL
    AND credit_review_amount >= deposit_amount - quoted_amount)))
);
ALTER TABLE occasion_enquiries ADD CONSTRAINT occasion_credit_nonnegative_check
 CHECK (credit_review_amount IS NULL OR credit_review_amount >= 0);
