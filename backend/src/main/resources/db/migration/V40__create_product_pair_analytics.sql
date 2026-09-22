CREATE TABLE analytics_product_pair_daily (
    business_date DATE NOT NULL,

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    product_a_id BIGINT NOT NULL
        REFERENCES products(id),

    product_b_id BIGINT NOT NULL
        REFERENCES products(id),

    pair_order_count BIGINT NOT NULL DEFAULT 0,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (
        business_date,
        branch_id,
        product_a_id,
        product_b_id
    ),

    CONSTRAINT ck_analytics_product_pair_order
        CHECK (
            product_a_id < product_b_id
        ),

    CONSTRAINT ck_analytics_product_pair_count
        CHECK (
            pair_order_count >= 0
        )
);


CREATE INDEX idx_analytics_product_pair_a_date
    ON analytics_product_pair_daily (
        product_a_id,
        business_date DESC
    );


CREATE INDEX idx_analytics_product_pair_b_date
    ON analytics_product_pair_daily (
        product_b_id,
        business_date DESC
    );


CREATE INDEX idx_analytics_product_pair_branch_date
    ON analytics_product_pair_daily (
        branch_id,
        business_date DESC
    );


CREATE INDEX idx_analytics_product_pair_count
    ON analytics_product_pair_daily (
        pair_order_count DESC
    );
