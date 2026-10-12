ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0);
ALTER TABLE orders ADD CONSTRAINT chk_delivery_fee_fulfillment CHECK
    (fulfillment_type = 'DELIVERY' OR delivery_fee = 0);
CREATE TABLE delivery_economics_snapshots (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id),
    version VARCHAR(60) NOT NULL,
    food_cost NUMERIC(12,2) NOT NULL CHECK (food_cost >= 0),
    packaging_cost NUMERIC(12,2) NOT NULL CHECK (packaging_cost >= 0),
    labour_cost NUMERIC(12,2) NOT NULL CHECK (labour_cost >= 0),
    waste_cost NUMERIC(12,2) NOT NULL CHECK (waste_cost >= 0),
    payment_cost NUMERIC(12,2) NOT NULL CHECK (payment_cost >= 0),
    journey_cost NUMERIC(12,2) NOT NULL CHECK (journey_cost >= 0),
    remedy_cost NUMERIC(12,2) NOT NULL CHECK (remedy_cost >= 0),
    delivery_fee NUMERIC(10,2) NOT NULL CHECK (delivery_fee >= 0),
    contribution NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
