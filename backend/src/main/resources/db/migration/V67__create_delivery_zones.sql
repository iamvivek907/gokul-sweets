CREATE TABLE delivery_zones (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    locality_key VARCHAR(120) NOT NULL,
    postal_code CHAR(6) NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    rider_paused BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_delivery_zone_branch_area UNIQUE (branch_id, locality_key, postal_code),
    CONSTRAINT chk_delivery_zone_hours CHECK (closes_at > opens_at),
    CONSTRAINT chk_delivery_zone_postal CHECK (postal_code ~ '^[0-9]{6}$')
);
CREATE INDEX idx_delivery_zone_area ON delivery_zones (postal_code, locality_key) WHERE active;

CREATE TABLE delivery_zone_products (
    zone_id BIGINT NOT NULL REFERENCES delivery_zones(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    PRIMARY KEY (zone_id, product_id)
);
