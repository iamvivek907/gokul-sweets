ALTER TABLE reviews ADD COLUMN show_customer_name BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_reviews_public_branch_id ON reviews (branch_id, id DESC) WHERE review_status = 'PUBLISHED' AND comment IS NOT NULL;
