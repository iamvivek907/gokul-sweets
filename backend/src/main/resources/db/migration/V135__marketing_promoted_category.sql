-- Preserve the historical target even if catalogue records are later removed.
-- Delivery looks up this exact ID and fails closed; history must not block catalogue cleanup.
ALTER TABLE marketing_push_deliveries ADD COLUMN promoted_category_id BIGINT;
