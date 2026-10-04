CREATE TABLE customer_location_lookup_limits (
 environment VARCHAR(10) NOT NULL,subject_id UUID NOT NULL REFERENCES verified_customer_subjects(id),
 last_requested_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(environment,subject_id)
);
