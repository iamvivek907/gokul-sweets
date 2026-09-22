ALTER TABLE analytics_customer_metrics
    ADD COLUMN previous_purchase_date DATE,

    ADD COLUMN expected_purchase_gap_days DECIMAL(10,2),

    ADD COLUMN last_purchase_gap_days INTEGER;


CREATE INDEX idx_analytics_customer_metrics_days_since_last
    ON analytics_customer_metrics (
        days_since_last_purchase
    );


CREATE INDEX idx_analytics_customer_metrics_expected_gap
    ON analytics_customer_metrics (
        expected_purchase_gap_days
    );
