ALTER TABLE staff_push_subscriptions ADD COLUMN paused_at TIMESTAMPTZ;
ALTER TABLE customer_push_subscriptions ADD COLUMN paused_at TIMESTAMPTZ;
