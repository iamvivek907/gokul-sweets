CREATE TABLE menu_service_policies (
 branch_id BIGINT PRIMARY KEY REFERENCES branches(id),
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE menu_service_items (
 branch_product_id BIGINT PRIMARY KEY REFERENCES branch_products(id),
 starts_at TIME,
 ends_at TIME,
 weekdays INTEGER NOT NULL DEFAULT 127 CHECK (weekdays BETWEEN 1 AND 127),
 sold_out BOOLEAN NOT NULL DEFAULT FALSE,
 requires_branch_product_id BIGINT REFERENCES branch_products(id),
 CHECK ((starts_at IS NULL) = (ends_at IS NULL)),
 CHECK (starts_at IS NULL OR starts_at <> ends_at),
 CHECK (requires_branch_product_id IS NULL OR requires_branch_product_id <> branch_product_id)
);
