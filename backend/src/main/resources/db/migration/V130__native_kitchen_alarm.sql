ALTER TABLE print_station_controls ADD COLUMN alarm_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE print_station_controls ADD COLUMN alarm_test_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE print_station_controls ADD COLUMN alarm_test_requested_at TIMESTAMPTZ;
ALTER TABLE print_station_controls ADD COLUMN alarm_last_seen_at TIMESTAMPTZ;
ALTER TABLE print_station_controls ADD COLUMN alarm_status VARCHAR(32);
ALTER TABLE print_station_controls ADD COLUMN alarm_test_receipt BIGINT NOT NULL DEFAULT 0;
