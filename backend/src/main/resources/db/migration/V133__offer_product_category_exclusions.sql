-- Per-offer exclusions are separate from loyalty earning/redemption exclusions.
CREATE TABLE rebate_excluded_products (
    rebate_id BIGINT NOT NULL REFERENCES rebates(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    PRIMARY KEY (rebate_id, product_id)
);
CREATE TABLE rebate_excluded_categories (
    rebate_id BIGINT NOT NULL REFERENCES rebates(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    PRIMARY KEY (rebate_id, category_id)
);
CREATE INDEX idx_rebate_excluded_products_product ON rebate_excluded_products(product_id);
CREATE INDEX idx_rebate_excluded_categories_category ON rebate_excluded_categories(category_id);
