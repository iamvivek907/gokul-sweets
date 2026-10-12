CREATE TABLE occasion_production_allocations (
    enquiry_id UUID NOT NULL REFERENCES occasion_enquiries(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(8) NOT NULL CHECK (unit IN ('PIECE','GRAM')),
    expected_ready_at TIMESTAMP NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PLANNED','HELD','COMMITTED','RELEASED')),
    approved_by VARCHAR(150) NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (enquiry_id, product_id)
);
-- Dedicated capacity is never included in inventory_daily_allocations or public availability.
