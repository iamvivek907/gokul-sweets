ALTER TABLE occasion_enquiries ADD COLUMN request_hash CHAR(64);

CREATE INDEX idx_occasion_enquiries_recent_request
    ON occasion_enquiries(environment, subject_id, request_hash, created_at DESC);
