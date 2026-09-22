package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsRefreshService {

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional
    public AnalyticsRefreshResponse rebuildAll() {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.REPORT_VIEW
                );

        long startedAt =
                System.currentTimeMillis();


        /*
         * Delete children/derived summaries only.
         *
         * These tables contain no source-of-truth business data.
         */
        jdbcTemplate.update(
                "DELETE FROM analytics_product_pair_daily"
        );

        jdbcTemplate.update(
                "DELETE FROM analytics_product_daily"
        );

        jdbcTemplate.update(
                "DELETE FROM analytics_sales_hourly"
        );

        jdbcTemplate.update(
                "DELETE FROM analytics_branch_daily"
        );

        jdbcTemplate.update(
                "DELETE FROM analytics_sales_daily"
        );

        jdbcTemplate.update(
                "DELETE FROM analytics_customer_metrics"
        );


        int salesDaily =
                jdbcTemplate.update(
                        SALES_DAILY_SQL
                );

        int branchDaily =
                jdbcTemplate.update(
                        BRANCH_DAILY_SQL
                );

        int salesHourly =
                jdbcTemplate.update(
                        SALES_HOURLY_SQL
                );

        int productDaily =
                jdbcTemplate.update(
                        PRODUCT_DAILY_SQL
                );

        int productPairDaily =
                jdbcTemplate.update(
                        PRODUCT_PAIR_DAILY_SQL
                );

        int customerMetrics =
                jdbcTemplate.update(
                        CUSTOMER_METRICS_SQL
                );


        long durationMs =
                System.currentTimeMillis()
                        - startedAt;


        log.info(
                "Reporting analytics rebuild completed: salesDaily={}, branchDaily={}, salesHourly={}, productDaily={}, productPairDaily={}, customerMetrics={}, durationMs={}",
                salesDaily,
                branchDaily,
                salesHourly,
                productDaily,
                productPairDaily,
                customerMetrics,
                durationMs
        );


        return new AnalyticsRefreshResponse(
                salesDaily,
                branchDaily,
                salesHourly,
                productDaily,
                customerMetrics,
                durationMs
        );
    }


    private static final String SALES_DAILY_SQL = """
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
                    ps.slot_date AS business_date,
                    COUNT(*) AS completed_orders,
                    COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
                    COALESCE(SUM(o.subtotal), 0) AS subtotal_amount,
                    COALESCE(SUM(o.tax_amount), 0) AS tax_amount,
                    COALESCE(SUM(o.priority_charge), 0) AS priority_charge_amount,
                    COALESCE(SUM(o.rebate_discount_amount), 0) AS rebate_discount_amount,
                    COALESCE(SUM(o.total_amount), 0) AS net_revenue
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date
            ) order_rollup
            LEFT JOIN (
                SELECT
                    ps.slot_date AS business_date,
                    COALESCE(SUM(oi.quantity), 0) AS units_sold
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                JOIN order_items oi
                    ON oi.order_id = o.id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date
            ) item_rollup
                ON item_rollup.business_date = order_rollup.business_date
            """;


    /*
     * Branch-level order money uses a separate order aggregate
     * so joining order_items cannot multiply monetary totals.
     */
    private static final String BRANCH_DAILY_SQL = """
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
                    ps.slot_date AS business_date,
                    o.branch_id,
                    COUNT(*) AS completed_orders,
                    COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
                    COALESCE(SUM(o.subtotal), 0) AS subtotal_amount,
                    COALESCE(SUM(o.tax_amount), 0) AS tax_amount,
                    COALESCE(SUM(o.priority_charge), 0) AS priority_charge_amount,
                    COALESCE(SUM(o.rebate_discount_amount), 0) AS rebate_discount_amount,
                    COALESCE(SUM(o.total_amount), 0) AS net_revenue
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date,
                    o.branch_id
            ) order_rollup
            LEFT JOIN (
                SELECT
                    ps.slot_date AS business_date,
                    o.branch_id,
                    COALESCE(SUM(oi.quantity), 0) AS units_sold
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                JOIN order_items oi
                    ON oi.order_id = o.id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date,
                    o.branch_id
            ) item_rollup
                ON item_rollup.business_date = order_rollup.business_date
               AND item_rollup.branch_id = order_rollup.branch_id
            """;


    private static final String SALES_HOURLY_SQL = """
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
                    ps.slot_date AS business_date,
                    o.branch_id,
                    EXTRACT(HOUR FROM ps.start_time)::SMALLINT AS pickup_hour,
                    COUNT(*) AS completed_orders,
                    COUNT(DISTINCT o.customer_contact_id) AS unique_customers,
                    COALESCE(SUM(o.total_amount), 0) AS net_revenue
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date,
                    o.branch_id,
                    EXTRACT(HOUR FROM ps.start_time)
            ) order_rollup
            LEFT JOIN (
                SELECT
                    ps.slot_date AS business_date,
                    o.branch_id,
                    EXTRACT(HOUR FROM ps.start_time)::SMALLINT AS pickup_hour,
                    COALESCE(SUM(oi.quantity), 0) AS units_sold
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                JOIN order_items oi
                    ON oi.order_id = o.id
                WHERE o.order_status = 'PICKED_UP'
                GROUP BY
                    ps.slot_date,
                    o.branch_id,
                    EXTRACT(HOUR FROM ps.start_time)
            ) item_rollup
                ON item_rollup.business_date = order_rollup.business_date
               AND item_rollup.branch_id = order_rollup.branch_id
               AND item_rollup.pickup_hour = order_rollup.pickup_hour
            """;


    private static final String PRODUCT_DAILY_SQL = """
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
                ps.slot_date,
                o.branch_id,
                oi.product_id,
                p.category_id,
                COUNT(DISTINCT o.id),
                COALESCE(SUM(oi.quantity), 0),
                COALESCE(SUM(oi.line_total), 0),
                COUNT(DISTINCT o.customer_contact_id),
                CURRENT_TIMESTAMP
            FROM orders o
            JOIN pickup_slots ps
                ON ps.id = o.pickup_slot_id
            JOIN order_items oi
                ON oi.order_id = o.id
            JOIN products p
                ON p.id = oi.product_id
            WHERE o.order_status = 'PICKED_UP'
            GROUP BY
                ps.slot_date,
                o.branch_id,
                oi.product_id,
                p.category_id
            """;


    private static final String PRODUCT_PAIR_DAILY_SQL = """
            WITH order_products AS (
                SELECT DISTINCT
                    ps.slot_date AS business_date,
                    o.branch_id,
                    o.id AS order_id,
                    oi.product_id
                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                JOIN order_items oi
                    ON oi.order_id = o.id
                WHERE o.order_status = 'PICKED_UP'
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


    private static final String CUSTOMER_METRICS_SQL = """
            WITH purchases AS (
                SELECT
                    o.customer_contact_id,
                    o.id AS order_id,
                    o.total_amount,
                    o.updated_at,
                    ps.slot_date AS purchase_date,

                    LAG(ps.slot_date)
                        OVER (
                            PARTITION BY o.customer_contact_id
                            ORDER BY
                                ps.slot_date ASC,
                                o.id ASC
                        ) AS previous_purchase_date,

                    ROW_NUMBER()
                        OVER (
                            PARTITION BY o.customer_contact_id
                            ORDER BY
                                ps.slot_date DESC,
                                o.id DESC
                        ) AS reverse_purchase_number

                FROM orders o
                JOIN pickup_slots ps
                    ON ps.id = o.pickup_slot_id
                WHERE
                    o.order_status = 'PICKED_UP'
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
}
