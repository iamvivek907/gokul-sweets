CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    overall_rating SMALLINT NOT NULL,
    comment VARCHAR(1000),
    review_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_reviews_order UNIQUE (order_id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reviews_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_reviews_rating CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (review_status IN ('PUBLISHED', 'HIDDEN'))
);

CREATE TABLE review_items (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    rating SMALLINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_items_review_product UNIQUE (review_id, product_id),
    CONSTRAINT fk_review_items_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_review_items_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_branch_created ON reviews (branch_id, created_at DESC);
CREATE INDEX idx_reviews_status_created ON reviews (review_status, created_at DESC);
CREATE INDEX idx_review_items_product ON review_items (product_id, rating);
