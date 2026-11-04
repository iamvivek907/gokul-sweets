ALTER TABLE pickup_slots ADD COLUMN occasion_enquiry_id UUID REFERENCES occasion_enquiries(id);
ALTER TABLE pickup_slots ADD CONSTRAINT chk_occasion_pickup_private
    CHECK (occasion_enquiry_id IS NULL OR (NOT active AND NOT priority_enabled AND capacity = 1));
ALTER TABLE pickup_slots DROP CONSTRAINT uk_pickup_slots_branch_date_time;
CREATE UNIQUE INDEX uk_pickup_slots_branch_date_time
    ON pickup_slots(branch_id, slot_date, start_time, end_time)
    WHERE occasion_enquiry_id IS NULL;
CREATE INDEX idx_pickup_slots_occasion_enquiry ON pickup_slots(occasion_enquiry_id)
    WHERE occasion_enquiry_id IS NOT NULL;

-- Legacy rows were published by default. Keep visibly configured items and
-- require the branch to opt in the remaining items explicitly.
ALTER TABLE branch_products ALTER COLUMN occasion_published SET DEFAULT FALSE;
UPDATE branch_products SET occasion_published = FALSE
WHERE occasion_published AND NOT occasion_only AND occasion_lead_days = 0 AND occasion_piece_grams IS NULL;
