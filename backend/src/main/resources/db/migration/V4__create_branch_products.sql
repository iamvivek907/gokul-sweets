CREATE TABLE branch_products (
    id BIGSERIAL PRIMARY KEY,

    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    price_override NUMERIC(12, 2),

    available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_branch_products_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT fk_branch_products_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT uk_branch_products_branch_product
        UNIQUE (branch_id, product_id),

    CONSTRAINT chk_branch_products_price_override
        CHECK (price_override IS NULL OR price_override >= 0)
);

CREATE INDEX idx_branch_products_branch_id
    ON branch_products(branch_id);

CREATE INDEX idx_branch_products_product_id
    ON branch_products(product_id);

CREATE INDEX idx_branch_products_branch_available_order
    ON branch_products(branch_id, available, display_order);