ALTER TABLE occasion_enquiries ADD COLUMN order_id BIGINT UNIQUE REFERENCES orders(id);
ALTER TABLE occasion_payment_attempts ADD COLUMN paid_at TIMESTAMPTZ;

CREATE TABLE occasion_quote_lines (
    enquiry_id UUID NOT NULL REFERENCES occasion_enquiries(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(200) NOT NULL,
    sale_mode VARCHAR(20) NOT NULL CHECK (sale_mode IN ('UNIT', 'WEIGHT')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    weight_grams INTEGER,
    gross_amount NUMERIC(12,2) NOT NULL CHECK (gross_amount > 0),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    tax_amount NUMERIC(12,2) NOT NULL CHECK (tax_amount >= 0),
    cgst_rate NUMERIC(5,2) NOT NULL CHECK (cgst_rate >= 0),
    sgst_rate NUMERIC(5,2) NOT NULL CHECK (sgst_rate >= 0),
    hsn_sac_code VARCHAR(20),
    PRIMARY KEY (enquiry_id, product_id),
    CHECK ((sale_mode = 'UNIT' AND weight_grams IS NULL)
        OR (sale_mode = 'WEIGHT' AND weight_grams >= 250)),
    CHECK (gross_amount = subtotal + tax_amount)
);
