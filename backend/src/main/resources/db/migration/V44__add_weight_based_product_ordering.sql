ALTER TABLE products
    ADD COLUMN sale_mode VARCHAR(20) NOT NULL DEFAULT 'UNIT',
    ADD COLUMN minimum_weight_grams INTEGER,
    ADD COLUMN weight_step_grams INTEGER;

ALTER TABLE products
    ADD CONSTRAINT chk_products_sale_mode
        CHECK (sale_mode IN ('UNIT', 'WEIGHT')),
    ADD CONSTRAINT chk_products_weight_configuration
        CHECK (
            (
                sale_mode = 'UNIT'
                AND minimum_weight_grams IS NULL
                AND weight_step_grams IS NULL
            )
            OR
            (
                sale_mode = 'WEIGHT'
                AND minimum_weight_grams >= 250
                AND weight_step_grams > 0
            )
        );

UPDATE products product
SET sale_mode = 'WEIGHT',
    minimum_weight_grams = 250,
    weight_step_grams = 50
FROM categories category
WHERE product.category_id = category.id
  AND (
      UPPER(category.code) LIKE '%SWEET%'
      OR UPPER(category.name) LIKE '%SWEET%'
  );

ALTER TABLE order_items
    ADD COLUMN sale_mode VARCHAR(20) NOT NULL DEFAULT 'UNIT',
    ADD COLUMN weight_grams INTEGER;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_sale_mode
        CHECK (sale_mode IN ('UNIT', 'WEIGHT')),
    ADD CONSTRAINT chk_order_items_weight
        CHECK (
            (sale_mode = 'UNIT' AND weight_grams IS NULL)
            OR
            (sale_mode = 'WEIGHT' AND weight_grams >= 250)
        );
