ALTER TABLE staff_alert_deliveries ADD COLUMN reconnect_retry_used BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_staff_delivery_live_priority ON staff_alert_deliveries(catch_up, id) WHERE state='QUEUED';
