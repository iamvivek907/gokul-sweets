CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL,

    product_id BIGINT NOT NULL,

    product_name VARCHAR(200) NOT NULL,

    quantity INTEGER NOT NULL,

    unit_price DECIMAL(12, 2) NOT NULL,

    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,

    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    line_total DECIMAL(12, 2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_order_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_order_items_unit_price
        CHECK (unit_price >= 0),

    CONSTRAINT chk_order_items_tax_rate
        CHECK (tax_rate >= 0),

    CONSTRAINT chk_order_items_tax_amount
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_order_items_line_total
        CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);

CREATE INDEX idx_order_items_product_id
    ON order_items(product_id);