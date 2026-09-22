CREATE TABLE kot (
    id BIGSERIAL PRIMARY KEY,

    kot_number VARCHAR(50) NOT NULL,

    order_id BIGINT NOT NULL,

    branch_id BIGINT NOT NULL,

    started_by_staff_id BIGINT NOT NULL,

    started_by_staff_name VARCHAR(150) NOT NULL,

    created_at TIMESTAMP NOT NULL,

    first_printed_at TIMESTAMP NULL,

    last_printed_at TIMESTAMP NULL,

    print_count INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uk_kot_kot_number
        UNIQUE (kot_number),

    CONSTRAINT uk_kot_order_id
        UNIQUE (order_id),

    CONSTRAINT fk_kot_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT fk_kot_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id),

    CONSTRAINT chk_kot_print_count
        CHECK (print_count >= 0)
);


CREATE INDEX idx_kot_branch_id
    ON kot(branch_id);


CREATE INDEX idx_kot_created_at
    ON kot(created_at);


CREATE INDEX idx_kot_started_by_staff_id
    ON kot(started_by_staff_id);


CREATE TABLE kot_items (
    id BIGSERIAL PRIMARY KEY,

    kot_id BIGINT NOT NULL,

    product_id BIGINT NOT NULL,

    product_name VARCHAR(200) NOT NULL,

    quantity INTEGER NOT NULL,

    display_order INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_kot_items_kot
        FOREIGN KEY (kot_id)
        REFERENCES kot(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_kot_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_kot_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_kot_items_display_order
        CHECK (display_order >= 0)
);


CREATE INDEX idx_kot_items_kot_id
    ON kot_items(kot_id);


CREATE INDEX idx_kot_items_product_id
    ON kot_items(product_id);