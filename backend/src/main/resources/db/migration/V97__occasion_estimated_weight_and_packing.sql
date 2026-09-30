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
