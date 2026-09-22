CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,

    category_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),

    base_price NUMERIC(12, 2) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
);

CREATE INDEX idx_products_category_id
    ON products(category_id);

CREATE INDEX idx_products_active
    ON products(active);