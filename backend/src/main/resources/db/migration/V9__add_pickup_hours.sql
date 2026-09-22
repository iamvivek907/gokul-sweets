ALTER TABLE branch_pickup_settings
ADD COLUMN opening_time TIME,
ADD COLUMN closing_time TIME;

UPDATE branch_pickup_settings
SET opening_time = '09:00:00',
    closing_time = '21:00:00'
WHERE opening_time IS NULL
   OR closing_time IS NULL;

ALTER TABLE branch_pickup_settings
ALTER COLUMN opening_time SET NOT NULL;

ALTER TABLE branch_pickup_settings
ALTER COLUMN closing_time SET NOT NULL;

ALTER TABLE branch_pickup_settings
ADD CONSTRAINT chk_branch_pickup_settings_time
CHECK (closing_time > opening_time);