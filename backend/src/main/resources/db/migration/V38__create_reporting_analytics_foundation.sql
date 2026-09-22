CREATE TABLE analytics_sales_daily (
    business_date DATE PRIMARY KEY,

    completed_orders BIGINT NOT NULL DEFAULT 0,

    unique_customers BIGINT NOT NULL DEFAULT 0,

    units_sold BIGINT NOT NULL DEFAULT 0,

    subtotal_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    tax_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    priority_charge_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    rebate_discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    net_revenue DECIMAL(14,2) NOT NULL DEFAULT 0,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE analytics_branch_daily (
    business_date DATE NOT NULL,

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    completed_orders BIGINT NOT NULL DEFAULT 0,

    unique_customers BIGINT NOT NULL DEFAULT 0,

    units_sold BIGINT NOT NULL DEFAULT 0,

    subtotal_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    tax_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    priority_charge_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    rebate_discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0,

    net_revenue DECIMAL(14,2) NOT NULL DEFAULT 0,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (
        business_date,
        branch_id
    )
);


CREATE INDEX idx_analytics_branch_daily_branch_date
    ON analytics_branch_daily (
        branch_id,
        business_date DESC
    );


CREATE TABLE analytics_sales_hourly (
    business_date DATE NOT NULL,

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    pickup_hour SMALLINT NOT NULL,

    completed_orders BIGINT NOT NULL DEFAULT 0,

    unique_customers BIGINT NOT NULL DEFAULT 0,

    units_sold BIGINT NOT NULL DEFAULT 0,

    net_revenue DECIMAL(14,2) NOT NULL DEFAULT 0,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (
        business_date,
        branch_id,
        pickup_hour
    ),

    CONSTRAINT ck_analytics_sales_hourly_hour
        CHECK (
            pickup_hour >= 0
            AND pickup_hour <= 23
        )
);


CREATE INDEX idx_analytics_sales_hourly_branch_date
    ON analytics_sales_hourly (
        branch_id,
        business_date DESC,
        pickup_hour
    );


CREATE TABLE analytics_product_daily (
    business_date DATE NOT NULL,

    branch_id BIGINT NOT NULL
        REFERENCES branches(id),

    product_id BIGINT NOT NULL
        REFERENCES products(id),

    category_id BIGINT NOT NULL
        REFERENCES categories(id),

    order_count BIGINT NOT NULL DEFAULT 0,

    quantity_sold BIGINT NOT NULL DEFAULT 0,

    gross_item_revenue DECIMAL(14,2) NOT NULL DEFAULT 0,

    unique_customers BIGINT NOT NULL DEFAULT 0,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (
        business_date,
        branch_id,
        product_id
    )
);


CREATE INDEX idx_analytics_product_daily_product_date
    ON analytics_product_daily (
        product_id,
        business_date DESC
    );


CREATE INDEX idx_analytics_product_daily_category_date
    ON analytics_product_daily (
        category_id,
        business_date DESC
    );


CREATE INDEX idx_analytics_product_daily_branch_date
    ON analytics_product_daily (
        branch_id,
        business_date DESC
    );


CREATE TABLE analytics_customer_metrics (
    customer_contact_id BIGINT PRIMARY KEY
        REFERENCES customer_contacts(id),

    first_purchase_at TIMESTAMP,

    last_purchase_at TIMESTAMP,

    lifetime_orders BIGINT NOT NULL DEFAULT 0,

    lifetime_spend DECIMAL(14,2) NOT NULL DEFAULT 0,

    average_order_value DECIMAL(14,2) NOT NULL DEFAULT 0,

    orders_30d BIGINT NOT NULL DEFAULT 0,

    orders_90d BIGINT NOT NULL DEFAULT 0,

    orders_365d BIGINT NOT NULL DEFAULT 0,

    spend_30d DECIMAL(14,2) NOT NULL DEFAULT 0,

    spend_90d DECIMAL(14,2) NOT NULL DEFAULT 0,

    spend_365d DECIMAL(14,2) NOT NULL DEFAULT 0,

    days_since_last_purchase INTEGER,

    refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_analytics_customer_metrics_last_purchase
    ON analytics_customer_metrics (
        last_purchase_at DESC
    );


CREATE INDEX idx_analytics_customer_metrics_lifetime_spend
    ON analytics_customer_metrics (
        lifetime_spend DESC
    );
