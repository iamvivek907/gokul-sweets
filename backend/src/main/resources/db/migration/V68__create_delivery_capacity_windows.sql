CREATE TABLE delivery_capacity_windows (
    id BIGSERIAL PRIMARY KEY,
    zone_id BIGINT NOT NULL REFERENCES delivery_zones(id) ON DELETE CASCADE,
    service_date DATE NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    rider_capacity INTEGER NOT NULL,
    reserved_count INTEGER NOT NULL DEFAULT 0,
    paused BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_delivery_window UNIQUE (zone_id, service_date, starts_at, ends_at),
    CONSTRAINT chk_delivery_window_hours CHECK (ends_at > starts_at),
    CONSTRAINT chk_delivery_window_capacity CHECK (rider_capacity > 0 AND reserved_count >= 0
        AND reserved_count <= rider_capacity)
);
CREATE INDEX idx_delivery_window_zone_date ON delivery_capacity_windows (zone_id, service_date);
