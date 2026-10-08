-- Cleanup configuration is isolated per deployment. Only the latest run is retained.
CREATE TABLE data_cleanup_settings (
 environment VARCHAR(8) PRIMARY KEY CHECK (environment IN ('DEV','PROD')),
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 daily_time TIME NOT NULL DEFAULT '03:30',
 retention_days INTEGER NOT NULL DEFAULT 90 CHECK (retention_days BETWEEN 30 AND 3650),
 revision BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_by BIGINT REFERENCES staff_users(id),
 last_scheduled_date DATE,
 last_started_at TIMESTAMPTZ,
 last_finished_at TIMESTAMPTZ,
 last_trigger VARCHAR(12),
 last_actor BIGINT REFERENCES staff_users(id),
 last_status VARCHAR(12) NOT NULL DEFAULT 'NEVER' CHECK (last_status IN ('NEVER','RUNNING','SUCCEEDED','FAILED')),
 last_error VARCHAR(300),
 last_counts JSONB NOT NULL DEFAULT '{}',
 lease_token UUID,
 lease_until TIMESTAMPTZ
);
INSERT INTO data_cleanup_settings(environment) VALUES ('DEV'), ('PROD');
CREATE INDEX idx_customer_notification_cleanup ON customer_notification_events(environment,created_at,id) WHERE read_at IS NOT NULL;
CREATE INDEX idx_staff_notification_cleanup ON staff_order_alerts(environment,created_at,id);
-- FK lookup indexes also bound child deletion and delivery protection checks.
CREATE INDEX idx_staff_alert_delivery_event ON staff_alert_deliveries(event_id);
