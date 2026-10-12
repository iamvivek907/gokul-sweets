CREATE TABLE delivery_zone_boundaries (
    zone_id BIGINT PRIMARY KEY REFERENCES delivery_zones(id) ON DELETE CASCADE,
    vertices JSONB NOT NULL,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_boundary_vertices_array CHECK (jsonb_typeof(vertices) = 'array')
);
