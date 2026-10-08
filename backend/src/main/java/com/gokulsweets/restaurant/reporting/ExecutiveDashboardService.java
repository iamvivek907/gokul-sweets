package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.*;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Coordinates executive dashboard operations. */
@Service
@RequiredArgsConstructor
public class ExecutiveDashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public ExecutiveDashboardOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ExecutiveDashboardService.class, "getOptions()");
        try {
            requireReportView();
            List<ReportBranchOptionResponse> branches =
                    jdbcTemplate.query(
                            """
                            SELECT
                                id,
                                code,
                                name
                            FROM branches
                            WHERE active = TRUE
                            ORDER BY
                                name ASC,
                                id ASC
                            """,
                            (rs, rowNum) ->
                                    new ReportBranchOptionResponse(
                                            rs.getLong("id"),
                                            rs.getString("code"),
                                            rs.getString("name")));
            return new ExecutiveDashboardOptionsResponse(branches);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ExecutiveDashboardService.class, "getOptions()");
        }
    }

    /**
     * Returns dashboard.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get dashboard result
     */
    @Transactional(readOnly = true)
    public ExecutiveDashboardResponse getDashboard(
            LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "getDashboard(LocalDate,LocalDate,Long)");
        try {
            requireReportView();
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            LocalDate safeTo = toDate == null ? today : toDate;
            LocalDate safeFrom = fromDate == null ? safeTo.minusDays(6) : fromDate;
            if (safeFrom.isAfter(safeTo)) {
                throw new IllegalArgumentException("From date cannot be after to date.");
            }
            long inclusiveDays = ChronoUnit.DAYS.between(safeFrom, safeTo) + 1;
            if (inclusiveDays > 366) {
                throw new IllegalArgumentException(
                        "Executive dashboard date range cannot exceed 366 days.");
            }
            LocalDate comparisonTo = safeFrom.minusDays(1);
            LocalDate comparisonFrom = comparisonTo.minusDays(inclusiveDays - 1);
            Aggregate current = loadAggregate(safeFrom, safeTo, branchId);
            Aggregate previous = loadAggregate(comparisonFrom, comparisonTo, branchId);
            BigDecimal averageOrderValue =
                    current.orders == 0
                            ? ZERO
                            : current.revenue.divide(
                                    BigDecimal.valueOf(current.orders), 2, RoundingMode.HALF_UP);
            ExecutiveDashboardKpiResponse kpis =
                    new ExecutiveDashboardKpiResponse(
                            current.revenue,
                            current.orders,
                            current.units,
                            current.customers,
                            averageOrderValue,
                            current.discount,
                            percentChange(current.revenue, previous.revenue),
                            percentChange(
                                    BigDecimal.valueOf(current.orders),
                                    BigDecimal.valueOf(previous.orders)),
                            percentChange(
                                    BigDecimal.valueOf(current.customers),
                                    BigDecimal.valueOf(previous.customers)));
            return new ExecutiveDashboardResponse(
                    safeFrom,
                    safeTo,
                    comparisonFrom,
                    comparisonTo,
                    branchId,
                    kpis,
                    loadTrend(safeFrom, safeTo, branchId),
                    loadBranches(safeFrom, safeTo, branchId),
                    loadHourly(safeFrom, safeTo, branchId),
                    loadHighlights(safeFrom, safeTo, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "getDashboard(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Loads aggregate.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the load aggregate result
     */
    private Aggregate loadAggregate(LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "loadAggregate(LocalDate,LocalDate,Long)");
        try {
            if (branchId == null) {
                return jdbcTemplate.queryForObject(
                        """
                        SELECT
                            COALESCE(SUM(net_revenue), 0) AS revenue,
                            COALESCE(SUM(completed_orders), 0) AS orders,
                            COALESCE(SUM(units_sold), 0) AS units,
                            COALESCE(SUM(unique_customers), 0) AS customers,
                            COALESCE(SUM(rebate_discount_amount), 0) AS discount
                        FROM analytics_sales_daily
                        WHERE business_date BETWEEN ? AND ?
                        """,
                        (rs, rowNum) ->
                                new Aggregate(
                                        rs.getBigDecimal("revenue"),
                                        rs.getLong("orders"),
                                        rs.getLong("units"),
                                        rs.getLong("customers"),
                                        rs.getBigDecimal("discount")),
                        Date.valueOf(fromDate),
                        Date.valueOf(toDate));
            }
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        COALESCE(SUM(net_revenue), 0) AS revenue,
                        COALESCE(SUM(completed_orders), 0) AS orders,
                        COALESCE(SUM(units_sold), 0) AS units,
                        COALESCE(SUM(unique_customers), 0) AS customers,
                        COALESCE(SUM(rebate_discount_amount), 0) AS discount
                    FROM analytics_branch_daily
                    WHERE business_date BETWEEN ? AND ?
                      AND branch_id = ?
                    """,
                    (rs, rowNum) ->
                            new Aggregate(
                                    rs.getBigDecimal("revenue"),
                                    rs.getLong("orders"),
                                    rs.getLong("units"),
                                    rs.getLong("customers"),
                                    rs.getBigDecimal("discount")),
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "loadAggregate(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Loads trend.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the load trend result
     */
    private List<ExecutiveDashboardTrendPointResponse> loadTrend(
            LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "loadTrend(LocalDate,LocalDate,Long)");
        try {
            if (branchId == null) {
                return jdbcTemplate.query(
                        """
                        SELECT
                            business_date,
                            net_revenue,
                            completed_orders,
                            units_sold,
                            unique_customers
                        FROM analytics_sales_daily
                        WHERE business_date BETWEEN ? AND ?
                        ORDER BY business_date ASC
                        """,
                        (rs, rowNum) ->
                                new ExecutiveDashboardTrendPointResponse(
                                        rs.getDate("business_date").toLocalDate(),
                                        rs.getBigDecimal("net_revenue"),
                                        rs.getLong("completed_orders"),
                                        rs.getLong("units_sold"),
                                        rs.getLong("unique_customers")),
                        Date.valueOf(fromDate),
                        Date.valueOf(toDate));
            }
            return jdbcTemplate.query(
                    """
                    SELECT
                        business_date,
                        net_revenue,
                        completed_orders,
                        units_sold,
                        unique_customers
                    FROM analytics_branch_daily
                    WHERE business_date BETWEEN ? AND ?
                      AND branch_id = ?
                    ORDER BY business_date ASC
                    """,
                    (rs, rowNum) ->
                            new ExecutiveDashboardTrendPointResponse(
                                    rs.getDate("business_date").toLocalDate(),
                                    rs.getBigDecimal("net_revenue"),
                                    rs.getLong("completed_orders"),
                                    rs.getLong("units_sold"),
                                    rs.getLong("unique_customers")),
                    Date.valueOf(fromDate),
                    Date.valueOf(toDate),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "loadTrend(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Loads branches.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the load branches result
     */
    private List<ExecutiveDashboardBranchResponse> loadBranches(
            LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "loadBranches(LocalDate,LocalDate,Long)");
        try {
            String sql =
                    """
                    SELECT
                        abd.branch_id,
                        b.code,
                        b.name,
                        COALESCE(SUM(abd.net_revenue), 0) AS revenue,
                        COALESCE(SUM(abd.completed_orders), 0) AS orders,
                        COALESCE(SUM(abd.units_sold), 0) AS units
                    FROM analytics_branch_daily abd
                    JOIN branches b
                        ON b.id = abd.branch_id
                    WHERE abd.business_date BETWEEN ? AND ?
                    """
                            + (branchId == null ? "" : " AND abd.branch_id = ? ")
                            + """
                              GROUP BY
                                  abd.branch_id,
                                  b.code,
                                  b.name
                              ORDER BY
                                  revenue DESC,
                                  abd.branch_id ASC
                              """;
            Object[] args =
                    branchId == null
                            ? new Object[] {Date.valueOf(fromDate), Date.valueOf(toDate)}
                            : new Object[] {Date.valueOf(fromDate), Date.valueOf(toDate), branchId};
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) ->
                            new ExecutiveDashboardBranchResponse(
                                    rs.getLong("branch_id"),
                                    rs.getString("code"),
                                    rs.getString("name"),
                                    rs.getBigDecimal("revenue"),
                                    rs.getLong("orders"),
                                    rs.getLong("units")),
                    args);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "loadBranches(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Loads hourly.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the load hourly result
     */
    private List<ExecutiveDashboardHourlyResponse> loadHourly(
            LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "loadHourly(LocalDate,LocalDate,Long)");
        try {
            String sql =
                    """
                    SELECT
                        pickup_hour,
                        COALESCE(SUM(completed_orders), 0) AS orders,
                        COALESCE(SUM(units_sold), 0) AS units,
                        COALESCE(SUM(net_revenue), 0) AS revenue
                    FROM analytics_sales_hourly
                    WHERE business_date BETWEEN ? AND ?
                    """
                            + (branchId == null ? "" : " AND branch_id = ? ")
                            + """
                              GROUP BY pickup_hour
                              ORDER BY pickup_hour ASC
                              """;
            Object[] args =
                    branchId == null
                            ? new Object[] {Date.valueOf(fromDate), Date.valueOf(toDate)}
                            : new Object[] {Date.valueOf(fromDate), Date.valueOf(toDate), branchId};
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) ->
                            new ExecutiveDashboardHourlyResponse(
                                    rs.getInt("pickup_hour"),
                                    rs.getLong("orders"),
                                    rs.getLong("units"),
                                    rs.getBigDecimal("revenue")),
                    args);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "loadHourly(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Loads highlights.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the load highlights result
     */
    private ExecutiveDashboardHighlightResponse loadHighlights(
            LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class,
                        "loadHighlights(LocalDate,LocalDate,Long)");
        try {
            Map<String, Object> strongestDay =
                    queryOne(
                            """
                            SELECT
                                business_date,
                                SUM(net_revenue) AS revenue
                            FROM analytics_branch_daily
                            WHERE business_date BETWEEN ? AND ?
                            """
                                    + (branchId == null ? "" : " AND branch_id = ? ")
                                    + """
                                      GROUP BY business_date
                                      ORDER BY
                                          revenue DESC,
                                          business_date ASC
                                      LIMIT 1
                                      """,
                            fromDate,
                            toDate,
                            branchId);
            Map<String, Object> strongestBranch =
                    queryOne(
                            """
                            SELECT
                                abd.branch_id,
                                b.name,
                                SUM(abd.net_revenue) AS revenue
                            FROM analytics_branch_daily abd
                            JOIN branches b
                                ON b.id = abd.branch_id
                            WHERE abd.business_date BETWEEN ? AND ?
                            """
                                    + (branchId == null ? "" : " AND abd.branch_id = ? ")
                                    + """
                                      GROUP BY
                                          abd.branch_id,
                                          b.name
                                      ORDER BY
                                          revenue DESC,
                                          abd.branch_id ASC
                                      LIMIT 1
                                      """,
                            fromDate,
                            toDate,
                            branchId);
            Map<String, Object> peakHour =
                    queryOne(
                            """
                            SELECT
                                pickup_hour,
                                SUM(completed_orders) AS orders
                            FROM analytics_sales_hourly
                            WHERE business_date BETWEEN ? AND ?
                            """
                                    + (branchId == null ? "" : " AND branch_id = ? ")
                                    + """
                                      GROUP BY pickup_hour
                                      ORDER BY
                                          orders DESC,
                                          pickup_hour ASC
                                      LIMIT 1
                                      """,
                            fromDate,
                            toDate,
                            branchId);
            Map<String, Object> topProduct =
                    queryOne(
                            """
                            SELECT
                                apd.product_id,
                                p.name,
                                SUM(apd.quantity_sold) AS quantity,
                                SUM(apd.gross_item_revenue) AS revenue
                            FROM analytics_product_daily apd
                            JOIN products p
                                ON p.id = apd.product_id
                            WHERE apd.business_date BETWEEN ? AND ?
                            """
                                    + (branchId == null ? "" : " AND apd.branch_id = ? ")
                                    + """
                                      GROUP BY
                                          apd.product_id,
                                          p.name
                                      ORDER BY
                                          quantity DESC,
                                          revenue DESC,
                                          apd.product_id ASC
                                      LIMIT 1
                                      """,
                            fromDate,
                            toDate,
                            branchId);
            return new ExecutiveDashboardHighlightResponse(
                    mapDate(strongestDay, "business_date"),
                    mapMoney(strongestDay, "revenue"),
                    mapLong(strongestBranch, "branch_id"),
                    mapString(strongestBranch, "name"),
                    mapMoney(strongestBranch, "revenue"),
                    mapInteger(peakHour, "pickup_hour"),
                    mapLongOrZero(peakHour, "orders"),
                    mapLong(topProduct, "product_id"),
                    mapString(topProduct, "name"),
                    mapLongOrZero(topProduct, "quantity"),
                    mapMoney(topProduct, "revenue"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "loadHighlights(LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Query one.
     *
     * @param sql the sql
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the query one result
     */
    private Map<String, Object> queryOne(
            String sql, LocalDate fromDate, LocalDate toDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class,
                        "queryOne(String,LocalDate,LocalDate,Long)");
        try {
            List<Map<String, Object>> rows;
            if (branchId == null) {
                rows = jdbcTemplate.queryForList(sql, Date.valueOf(fromDate), Date.valueOf(toDate));
            } else {
                rows =
                        jdbcTemplate.queryForList(
                                sql, Date.valueOf(fromDate), Date.valueOf(toDate), branchId);
            }
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "queryOne(String,LocalDate,LocalDate,Long)");
        }
    }

    /**
     * Percents change.
     *
     * @param current the current
     * @param previous the previous
     * @return the percent change result
     */
    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "percentChange(BigDecimal,BigDecimal)");
        try {
            if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                return current == null || current.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : new BigDecimal("100.00");
            }
            return current.subtract(previous)
                    .divide(previous, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "percentChange(BigDecimal,BigDecimal)");
        }
    }

    /**
     * Maps date.
     *
     * @param row the row
     * @param key the key
     * @return the map date result
     */
    private LocalDate mapDate(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "mapDate(Map<String,Object>,String)");
        try {
            Object value = row.get(key);
            if (value instanceof Date date) {
                return date.toLocalDate();
            }
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapDate(Map<String,Object>,String)");
        }
    }

    /**
     * Maps money.
     *
     * @param row the row
     * @param key the key
     * @return the map money result
     */
    private BigDecimal mapMoney(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "mapMoney(Map<String,Object>,String)");
        try {
            Object value = row.get(key);
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return ZERO;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapMoney(Map<String,Object>,String)");
        }
    }

    /**
     * Maps long.
     *
     * @param row the row
     * @param key the key
     * @return the map long result
     */
    private Long mapLong(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "mapLong(Map<String,Object>,String)");
        try {
            Object value = row.get(key);
            return value instanceof Number number ? number.longValue() : null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapLong(Map<String,Object>,String)");
        }
    }

    /**
     * Maps long or zero.
     *
     * @param row the row
     * @param key the key
     * @return the map long or zero result
     */
    private long mapLongOrZero(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class,
                        "mapLongOrZero(Map<String,Object>,String)");
        try {
            Long value = mapLong(row, key);
            return value == null ? 0L : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapLongOrZero(Map<String,Object>,String)");
        }
    }

    /**
     * Maps integer.
     *
     * @param row the row
     * @param key the key
     * @return the map integer result
     */
    private Integer mapInteger(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "mapInteger(Map<String,Object>,String)");
        try {
            Object value = row.get(key);
            return value instanceof Number number ? number.intValue() : null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapInteger(Map<String,Object>,String)");
        }
    }

    /**
     * Maps string.
     *
     * @param row the row
     * @param key the key
     * @return the map string result
     */
    private String mapString(Map<String, Object> row, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardService.class, "mapString(Map<String,Object>,String)");
        try {
            Object value = row.get(key);
            return value == null ? null : String.valueOf(value);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "mapString(Map<String,Object>,String)");
        }
    }

    /** Requires report view. */
    private void requireReportView() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ExecutiveDashboardService.class, "requireReportView()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REPORT_VIEW);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardService.class,
                    "requireReportView()");
        }
    }

    /** Immutable aggregate data contract. */
    private record Aggregate(
            BigDecimal revenue, long orders, long units, long customers, BigDecimal discount) {}
}
