CREATE TABLE tax_categories (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    hsn_sac_code VARCHAR(20),

    cgst_rate NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    sgst_rate NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    igst_rate NUMERIC(5, 2) NOT NULL DEFAULT 0.00,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tax_categories_cgst_rate
        CHECK (cgst_rate >= 0 AND cgst_rate <= 100),

    CONSTRAINT chk_tax_categories_sgst_rate
        CHECK (sgst_rate >= 0 AND sgst_rate <= 100),

    CONSTRAINT chk_tax_categories_igst_rate
        CHECK (igst_rate >= 0 AND igst_rate <= 100)
);

CREATE INDEX idx_tax_categories_active
    ON tax_categories(active);