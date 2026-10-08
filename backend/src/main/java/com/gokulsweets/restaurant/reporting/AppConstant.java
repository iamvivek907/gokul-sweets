package com.gokulsweets.restaurant.reporting;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.reporting package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    /** Creates a app constant instance. */
    private AppConstant() {}

    /** Original AnalyticsRefreshService.SALES_DAILY_SQL value; unchanged during extraction. */
    public static final String ANALYTICS_REFRESH_SERVICE_SALES_DAILY_SQL =
            """
INSERT INTO analytics_sales_daily (
    business_date,
    completed_orders,
    unique_customers,
    units_sold,
    subtotal_amount,
    tax_amount,
    priority_charge_amount,
    rebate_discount_amount,
    net_revenue,
    refreshed_at
)
SELECT
    order_rollup.business_date,
    order_rollup.completed_orders,
    order_rollup.unique_customers,
    COALESCE(item_rollup.units_sold, 0),
    order_rollup.subtotal_amount,
    order_rollup.tax_amount,
    order_rollup.priority_charge_amount,
    order_rollup.rebate_discount_amount,
    order_rollup.net_revenue,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        COUNT(*) AS completed_orders,
        COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
        COALESCE(SUM(o.subtotal+o.convenience_fee-o.convenience_fee_tax), 0) AS subtotal_amount,
        COALESCE(SUM(o.tax_amount+o.convenience_fee_tax), 0) AS tax_amount,
        COALESCE(SUM(o.priority_charge), 0) AS priority_charge_amount,
        COALESCE(SUM(o.rebate_discount_amount), 0) AS rebate_discount_amount,
        COALESCE(SUM(o.total_amount), 0) AS net_revenue
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date)
) order_rollup
LEFT JOIN (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        COALESCE(SUM(oi.quantity), 0) AS units_sold
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    JOIN order_items oi
        ON oi.order_id = o.id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date)
) item_rollup
    ON item_rollup.business_date = order_rollup.business_date
""";

    /** Original AnalyticsRefreshService.BRANCH_DAILY_SQL value; unchanged during extraction. */
    public static final String ANALYTICS_REFRESH_SERVICE_BRANCH_DAILY_SQL =
            """
INSERT INTO analytics_branch_daily (
    business_date,
    branch_id,
    completed_orders,
    unique_customers,
    units_sold,
    subtotal_amount,
    tax_amount,
    priority_charge_amount,
    rebate_discount_amount,
    net_revenue,
    refreshed_at
)
SELECT
    order_rollup.business_date,
    order_rollup.branch_id,
    order_rollup.completed_orders,
    order_rollup.unique_customers,
    COALESCE(item_rollup.units_sold, 0),
    order_rollup.subtotal_amount,
    order_rollup.tax_amount,
    order_rollup.priority_charge_amount,
    order_rollup.rebate_discount_amount,
    order_rollup.net_revenue,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        o.branch_id,
        COUNT(*) AS completed_orders,
        COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
        COALESCE(SUM(o.subtotal+o.convenience_fee-o.convenience_fee_tax), 0) AS subtotal_amount,
        COALESCE(SUM(o.tax_amount+o.convenience_fee_tax), 0) AS tax_amount,
        COALESCE(SUM(o.priority_charge), 0) AS priority_charge_amount,
        COALESCE(SUM(o.rebate_discount_amount), 0) AS rebate_discount_amount,
        COALESCE(SUM(o.total_amount), 0) AS net_revenue
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date),
        o.branch_id
) order_rollup
LEFT JOIN (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        o.branch_id,
        COALESCE(SUM(oi.quantity), 0) AS units_sold
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    JOIN order_items oi
        ON oi.order_id = o.id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date),
        o.branch_id
) item_rollup
    ON item_rollup.business_date = order_rollup.business_date
   AND item_rollup.branch_id = order_rollup.branch_id
""";

    /** Original AnalyticsRefreshService.SALES_HOURLY_SQL value; unchanged during extraction. */
    public static final String ANALYTICS_REFRESH_SERVICE_SALES_HOURLY_SQL =
            """
INSERT INTO analytics_sales_hourly (
    business_date,
    branch_id,
    pickup_hour,
    completed_orders,
    unique_customers,
    units_sold,
    net_revenue,
    refreshed_at
)
SELECT
    order_rollup.business_date,
    order_rollup.branch_id,
    order_rollup.pickup_hour,
    order_rollup.completed_orders,
    order_rollup.unique_customers,
    COALESCE(item_rollup.units_sold, 0),
    order_rollup.net_revenue,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        o.branch_id,
        EXTRACT(HOUR FROM COALESCE(ps.start_time, dw.starts_at))::SMALLINT AS pickup_hour,
        COUNT(*) AS completed_orders,
        COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
        COALESCE(SUM(o.total_amount), 0) AS net_revenue
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date),
        o.branch_id,
        EXTRACT(HOUR FROM COALESCE(ps.start_time, dw.starts_at))
) order_rollup
LEFT JOIN (
    SELECT
        COALESCE(ps.slot_date, dw.service_date) AS business_date,
        o.branch_id,
        EXTRACT(HOUR FROM COALESCE(ps.start_time, dw.starts_at))::SMALLINT AS pickup_hour,
        COALESCE(SUM(oi.quantity), 0) AS units_sold
    FROM orders o
    LEFT JOIN pickup_slots ps
        ON ps.id = o.pickup_slot_id
    LEFT JOIN delivery_capacity_windows dw
        ON dw.id = o.delivery_window_id
    JOIN order_items oi
        ON oi.order_id = o.id
    WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
    GROUP BY
        COALESCE(ps.slot_date, dw.service_date),
        o.branch_id,
        EXTRACT(HOUR FROM COALESCE(ps.start_time, dw.starts_at))
) item_rollup
    ON item_rollup.business_date = order_rollup.business_date
   AND item_rollup.branch_id = order_rollup.branch_id
   AND item_rollup.pickup_hour = order_rollup.pickup_hour
""";

    /** Original AnalyticsRefreshService.PRODUCT_DAILY_SQL value; unchanged during extraction. */
    public static final String ANALYTICS_REFRESH_SERVICE_PRODUCT_DAILY_SQL =
            """
            INSERT INTO analytics_product_daily (
                business_date,
                branch_id,
                product_id,
                category_id,
                order_count,
                quantity_sold,
                gross_item_revenue,
                unique_customers,
                refreshed_at
            )
            SELECT
                COALESCE(ps.slot_date, dw.service_date),
                o.branch_id,
                oi.product_id,
                p.category_id,
                COUNT(DISTINCT o.id),
                COALESCE(SUM(oi.quantity), 0),
                COALESCE(SUM(oi.line_total), 0),
                COUNT(DISTINCT o.customer_contact_id),
                CURRENT_TIMESTAMP
            FROM orders o
            LEFT JOIN pickup_slots ps
                ON ps.id = o.pickup_slot_id
                LEFT JOIN delivery_capacity_windows dw
                    ON dw.id = o.delivery_window_id
            JOIN order_items oi
                ON oi.order_id = o.id
            JOIN products p
                ON p.id = oi.product_id
            WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
            GROUP BY
                COALESCE(ps.slot_date, dw.service_date),
                o.branch_id,
                oi.product_id,
                p.category_id
            """;

    /**
     * Original AnalyticsRefreshService.PRODUCT_PAIR_DAILY_SQL value; unchanged during extraction.
     */
    public static final String ANALYTICS_REFRESH_SERVICE_PRODUCT_PAIR_DAILY_SQL =
            """
            WITH order_products AS (
                SELECT DISTINCT
                    COALESCE(ps.slot_date, dw.service_date) AS business_date,
                    o.branch_id,
                    o.id AS order_id,
                    oi.product_id
                FROM orders o
                LEFT JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                LEFT JOIN delivery_capacity_windows dw
                    ON dw.id = o.delivery_window_id
                JOIN order_items oi
                    ON oi.order_id = o.id
                WHERE o.order_status IN ('PICKED_UP', 'DELIVERED')
            )
            INSERT INTO analytics_product_pair_daily (
                business_date,
                branch_id,
                product_a_id,
                product_b_id,
                pair_order_count,
                refreshed_at
            )
            SELECT
                first_product.business_date,
                first_product.branch_id,
                first_product.product_id,
                second_product.product_id,
                COUNT(
                    DISTINCT first_product.order_id
                ),
                CURRENT_TIMESTAMP
            FROM order_products first_product
            JOIN order_products second_product
                ON second_product.order_id = first_product.order_id
               AND second_product.business_date = first_product.business_date
               AND second_product.branch_id = first_product.branch_id
               AND second_product.product_id > first_product.product_id
            GROUP BY
                first_product.business_date,
                first_product.branch_id,
                first_product.product_id,
                second_product.product_id
            """;

    /** Original AnalyticsRefreshService.CUSTOMER_METRICS_SQL value; unchanged during extraction. */
    public static final String ANALYTICS_REFRESH_SERVICE_CUSTOMER_METRICS_SQL =
            """
            WITH purchases AS (
                SELECT
                    o.customer_contact_id,
                    o.id AS order_id,
                    o.total_amount,
                    o.updated_at,
                    COALESCE(ps.slot_date, dw.service_date) AS purchase_date,

                    LAG(COALESCE(ps.slot_date, dw.service_date))
                        OVER (
                            PARTITION BY o.customer_contact_id
                            ORDER BY
                                COALESCE(ps.slot_date, dw.service_date) ASC,
                                o.id ASC
                        ) AS previous_purchase_date,

                    ROW_NUMBER()
                        OVER (
                            PARTITION BY o.customer_contact_id
                            ORDER BY
                                COALESCE(ps.slot_date, dw.service_date) DESC,
                                o.id DESC
                        ) AS reverse_purchase_number

                FROM orders o
                LEFT JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                LEFT JOIN delivery_capacity_windows dw
                    ON dw.id = o.delivery_window_id
                WHERE
                    o.order_status IN ('PICKED_UP', 'DELIVERED')
                    AND o.customer_contact_id IS NOT NULL
            ),
            purchase_gaps AS (
                SELECT
                    customer_contact_id,
                    order_id,
                    total_amount,
                    updated_at,
                    purchase_date,
                    previous_purchase_date,
                    reverse_purchase_number,

                    CASE
                        WHEN previous_purchase_date IS NULL
                            THEN NULL
                        ELSE
                            purchase_date
                            - previous_purchase_date
                    END AS gap_days

                FROM purchases
            ),
            customer_rollup AS (
                SELECT
                    customer_contact_id,

                    MIN(updated_at) AS first_purchase_at,

                    MAX(updated_at) AS last_purchase_at,

                    COUNT(*) AS lifetime_orders,

                    COALESCE(
                        SUM(total_amount),
                        0
                    ) AS lifetime_spend,

                    COALESCE(
                        AVG(total_amount),
                        0
                    ) AS average_order_value,

                    COUNT(*)
                        FILTER (
                            WHERE purchase_date >= CURRENT_DATE - INTERVAL '29 days'
                        ) AS orders_30d,

                    COUNT(*)
                        FILTER (
                            WHERE purchase_date >= CURRENT_DATE - INTERVAL '89 days'
                        ) AS orders_90d,

                    COUNT(*)
                        FILTER (
                            WHERE purchase_date >= CURRENT_DATE - INTERVAL '364 days'
                        ) AS orders_365d,

                    COALESCE(
                        SUM(total_amount)
                            FILTER (
                                WHERE purchase_date >= CURRENT_DATE - INTERVAL '29 days'
                            ),
                        0
                    ) AS spend_30d,

                    COALESCE(
                        SUM(total_amount)
                            FILTER (
                                WHERE purchase_date >= CURRENT_DATE - INTERVAL '89 days'
                            ),
                        0
                    ) AS spend_90d,

                    COALESCE(
                        SUM(total_amount)
                            FILTER (
                                WHERE purchase_date >= CURRENT_DATE - INTERVAL '364 days'
                            ),
                        0
                    ) AS spend_365d,

                    CURRENT_DATE
                        - MAX(purchase_date) AS days_since_last_purchase,

                    MAX(previous_purchase_date)
                        FILTER (
                            WHERE reverse_purchase_number = 1
                        ) AS previous_purchase_date,

                    ROUND(
                        COALESCE(
                            AVG(gap_days)
                                FILTER (
                                    WHERE
                                        gap_days IS NOT NULL
                                        AND reverse_purchase_number > 1
                                ),

                            AVG(gap_days)
                                FILTER (
                                    WHERE gap_days IS NOT NULL
                                )
                        )::NUMERIC,
                        2
                    ) AS expected_purchase_gap_days,

                    MAX(gap_days)
                        FILTER (
                            WHERE reverse_purchase_number = 1
                        ) AS last_purchase_gap_days

                FROM purchase_gaps
                GROUP BY customer_contact_id
            )
            INSERT INTO analytics_customer_metrics (
                customer_contact_id,
                first_purchase_at,
                last_purchase_at,
                lifetime_orders,
                lifetime_spend,
                average_order_value,
                orders_30d,
                orders_90d,
                orders_365d,
                spend_30d,
                spend_90d,
                spend_365d,
                days_since_last_purchase,
                previous_purchase_date,
                expected_purchase_gap_days,
                last_purchase_gap_days,
                refreshed_at
            )
            SELECT
                customer_contact_id,
                first_purchase_at,
                last_purchase_at,
                lifetime_orders,
                lifetime_spend,
                average_order_value,
                orders_30d,
                orders_90d,
                orders_365d,
                spend_30d,
                spend_90d,
                spend_365d,
                days_since_last_purchase,
                previous_purchase_date,
                expected_purchase_gap_days,
                last_purchase_gap_days,
                CURRENT_TIMESTAMP
            FROM customer_rollup
            """;

    /**
     * Original CustomerIntelligenceService.DEFAULT_PAGE_SIZE value; unchanged during extraction.
     */
    public static final int CUSTOMER_INTELLIGENCE_SERVICE_DEFAULT_PAGE_SIZE = 25;

    /** Original CustomerIntelligenceService.MAX_PAGE_SIZE value; unchanged during extraction. */
    public static final int CUSTOMER_INTELLIGENCE_SERVICE_MAX_PAGE_SIZE = 100;

    /** Original DemandForecastService.HISTORY_WEEKS value; unchanged during extraction. */
    public static final int DEMAND_FORECAST_SERVICE_HISTORY_WEEKS = 12;
}
