-- An automatically resolved completion still delivers one optional thank-you/review push.
-- Explicit customer acknowledgement always disables this exception.
ALTER TABLE customer_notification_events ADD COLUMN auto_acknowledged BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_customer_notification_ageing ON customer_notification_events(created_at, id) WHERE read_at IS NULL;
