ALTER TABLE products
ADD COLUMN tax_category_id BIGINT;

ALTER TABLE products
ADD CONSTRAINT fk_products_tax_category
    FOREIGN KEY (tax_category_id)
    REFERENCES tax_categories(id);

CREATE INDEX idx_products_tax_category_id
    ON products(tax_category_id);