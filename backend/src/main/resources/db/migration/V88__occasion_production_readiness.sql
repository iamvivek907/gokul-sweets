ALTER TABLE occasion_production_allocations
    ADD COLUMN ready_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    ADD COLUMN readiness_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_occasion_ready_quantity CHECK (ready_quantity >= 0 AND ready_quantity <= quantity),
    ADD CONSTRAINT ck_occasion_ready_revision CHECK (readiness_revision >= 0);

CREATE TABLE occasion_production_readiness_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enquiry_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    previous_quantity NUMERIC(14,3) NOT NULL,
    ready_quantity NUMERIC(14,3) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enquiry_id, product_id) REFERENCES occasion_production_allocations(enquiry_id, product_id)
);
