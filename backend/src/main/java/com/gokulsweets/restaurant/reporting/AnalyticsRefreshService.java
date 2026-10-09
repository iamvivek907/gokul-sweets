package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates analytics refresh operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsRefreshService {

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Rebuilds all.
     *
     * @return the rebuild all result
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AnalyticsRefreshResponse rebuildAll() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AnalyticsRefreshService.class, "rebuildAll()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REPORT_VIEW);
            return rebuildInternal();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AnalyticsRefreshService.class, "rebuildAll()");
        }
    }

    /** Runs without a staff session; invoked only by the internal maintenance job. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void refreshChangedOrders() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AnalyticsRefreshService.class, "refreshChangedOrders()");
        try {
            Boolean locked =
                    jdbcTemplate.queryForObject(
                            "SELECT pg_try_advisory_xact_lock(88321096)", Boolean.class);
            if (!Boolean.TRUE.equals(locked)) return;
            var source =
                    jdbcTemplate.queryForMap(
                            "SELECT COALESCE(MAX(id),0) AS n, MAX(updated_at) AS latest FROM"
                                    + " orders");
            var checkpoint =
                    jdbcTemplate.queryForList(
                            "SELECT highest_order_id, latest_order_update, business_date FROM"
                                    + " analytics_refresh_checkpoint WHERE id=1");
            var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            boolean sameDay =
                    !checkpoint.isEmpty()
                            && java.sql.Date.valueOf(today)
                                    .equals(checkpoint.getFirst().get("business_date"));
            if (sameDay
                    && source.get("n").equals(checkpoint.getFirst().get("highest_order_id"))
                    && java.util.Objects.equals(
                            source.get("latest"), checkpoint.getFirst().get("latest_order_update")))
                return;
            if (!sameDay || checkpoint.getFirst().get("latest_order_update") == null)
                rebuildInternal();
            else refreshAffectedSummaries(checkpoint.getFirst().get("latest_order_update"));
            jdbcTemplate.update(
                    """
INSERT INTO analytics_refresh_checkpoint(id,highest_order_id,latest_order_update,business_date) VALUES(1,?,?,?)
ON CONFLICT(id) DO UPDATE SET highest_order_id=EXCLUDED.highest_order_id,
  latest_order_update=EXCLUDED.latest_order_update,business_date=EXCLUDED.business_date,refreshed_at=CURRENT_TIMESTAMP
""",
                    source.get("n"),
                    source.get("latest"),
                    java.sql.Date.valueOf(today));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AnalyticsRefreshService.class,
                    "refreshChangedOrders()");
        }
    }

    /**
     * Refreshes affected summaries.
     *
     * @param since the since
     */
    private void refreshAffectedSummaries(Object since) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AnalyticsRefreshService.class, "refreshAffectedSummaries(Object)");
        try {
            var dates =
                    jdbcTemplate.queryForList(
                            """
SELECT DISTINCT COALESCE(ps.slot_date,dw.service_date) AS d FROM orders o
LEFT JOIN pickup_slots ps ON ps.id=o.pickup_slot_id
LEFT JOIN delivery_capacity_windows dw ON dw.id=o.delivery_window_id
WHERE o.updated_at>=? AND COALESCE(ps.slot_date,dw.service_date) IS NOT NULL
""",
                            java.sql.Date.class,
                            since);
            if (!dates.isEmpty()) {
                // The dates come from typed DB Date values; no caller text enters SQL.
                String in =
                        dates.stream()
                                .map(date -> "DATE '" + date.toLocalDate() + "'")
                                .collect(java.util.stream.Collectors.joining(","));
                String scope = "COALESCE(ps.slot_date,dw.service_date) IN (" + in + ")";
                for (String table :
                        java.util.List.of(
                                "analytics_product_pair_daily",
                                "analytics_product_daily",
                                "analytics_sales_hourly",
                                "analytics_branch_daily",
                                "analytics_sales_daily"))
                    jdbcTemplate.update(
                            "DELETE FROM " + table + " WHERE business_date IN (" + in + ")");
                for (String query :
                        java.util.List.of(
                                SALES_DAILY_SQL,
                                BRANCH_DAILY_SQL,
                                SALES_HOURLY_SQL,
                                PRODUCT_DAILY_SQL,
                                PRODUCT_PAIR_DAILY_SQL))
                    jdbcTemplate.update(
                            query.replace(
                                    "WHERE o.order_status IN",
                                    "WHERE " + scope + " AND o.order_status IN"));
            }
            var customers =
                    jdbcTemplate.queryForList(
                            "SELECT DISTINCT customer_contact_id FROM orders WHERE updated_at>=?"
                                    + " AND customer_contact_id IS NOT NULL",
                            Long.class,
                            since);
            if (!customers.isEmpty()) {
                String ids =
                        customers.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(","));
                jdbcTemplate.update(
                        "DELETE FROM analytics_customer_metrics WHERE customer_contact_id IN ("
                                + ids
                                + ")");
                jdbcTemplate.update(
                        CUSTOMER_METRICS_SQL.replace(
                                "AND o.customer_contact_id IS NOT NULL",
                                "AND o.customer_contact_id IN (" + ids + ")"));
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AnalyticsRefreshService.class,
                    "refreshAffectedSummaries(Object)");
        }
    }

    /**
     * Rebuilds internal.
     *
     * @return the rebuild internal result
     */
    private AnalyticsRefreshResponse rebuildInternal() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AnalyticsRefreshService.class, "rebuildInternal()");
        try {
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(88321096)", Object.class);
            long startedAt = System.currentTimeMillis();
            /*
             * Delete children/derived summaries only.
             *
             * These tables contain no source-of-truth business data.
             */
            jdbcTemplate.update("DELETE FROM analytics_product_pair_daily");
            jdbcTemplate.update("DELETE FROM analytics_product_daily");
            jdbcTemplate.update("DELETE FROM analytics_sales_hourly");
            jdbcTemplate.update("DELETE FROM analytics_branch_daily");
            jdbcTemplate.update("DELETE FROM analytics_sales_daily");
            jdbcTemplate.update("DELETE FROM analytics_customer_metrics");
            int salesDaily = jdbcTemplate.update(SALES_DAILY_SQL);
            int branchDaily = jdbcTemplate.update(BRANCH_DAILY_SQL);
            int salesHourly = jdbcTemplate.update(SALES_HOURLY_SQL);
            int productDaily = jdbcTemplate.update(PRODUCT_DAILY_SQL);
            int productPairDaily = jdbcTemplate.update(PRODUCT_PAIR_DAILY_SQL);
            int customerMetrics = jdbcTemplate.update(CUSTOMER_METRICS_SQL);
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info(
                    "Reporting analytics rebuild completed: salesDaily={}, branchDaily={},"
                            + " salesHourly={}, productDaily={}, productPairDaily={},"
                            + " customerMetrics={}, durationMs={}",
                    salesDaily,
                    branchDaily,
                    salesHourly,
                    productDaily,
                    productPairDaily,
                    customerMetrics,
                    durationMs);
            return new AnalyticsRefreshResponse(
                    salesDaily,
                    branchDaily,
                    salesHourly,
                    productDaily,
                    customerMetrics,
                    durationMs);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AnalyticsRefreshService.class, "rebuildInternal()");
        }
    }

    private static final String SALES_DAILY_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_SALES_DAILY_SQL;

    /*
     * Branch-level order money uses a separate order aggregate
     * so joining order_items cannot multiply monetary totals.
     */
    private static final String BRANCH_DAILY_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_BRANCH_DAILY_SQL;

    private static final String SALES_HOURLY_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_SALES_HOURLY_SQL;

    private static final String PRODUCT_DAILY_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_PRODUCT_DAILY_SQL;

    private static final String PRODUCT_PAIR_DAILY_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_PRODUCT_PAIR_DAILY_SQL;

    private static final String CUSTOMER_METRICS_SQL =
            AppConstant.ANALYTICS_REFRESH_SERVICE_CUSTOMER_METRICS_SQL;
}
