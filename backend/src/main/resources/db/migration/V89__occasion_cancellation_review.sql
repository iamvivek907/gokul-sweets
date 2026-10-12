ALTER TABLE occasion_enquiries DROP CONSTRAINT occasion_enquiries_status_check;
ALTER TABLE occasion_enquiries ADD CONSTRAINT occasion_enquiries_status_check CHECK (status IN
    ('REQUESTED','QUOTED','HELD','PAYMENT_PENDING','PAID','CONFIRMED','DECLINED','EXPIRED','CANCELLED'));
CREATE TABLE occasion_cancellation_reviews (
    enquiry_id UUID PRIMARY KEY REFERENCES occasion_enquiries(id),
    paid_amount NUMERIC(12,2) NOT NULL CHECK (paid_amount > 0),
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'REVIEW_REQUIRED' CHECK (state = 'REVIEW_REQUIRED'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
