package com.gokulsweets.restaurant.reporting;

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

@Service
@RequiredArgsConstructor
public class SalesIntelligenceService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    private static final BigDecimal ZERO =
            new BigDecimal(
                    "0.00"
            );

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public SalesIntelligenceResponse getSalesIntelligence(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.REPORT_VIEW
                );


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );

        LocalDate safeTo =
                toDate == null
                        ? today
                        : toDate;

        LocalDate safeFrom =
                fromDate == null
                        ? safeTo.minusDays(
                        29
                )
                        : fromDate;


        if (
                safeFrom.isAfter(
                        safeTo
                )
        ) {

            throw new IllegalArgumentException(
                    "From date cannot be after to date."
            );
        }


        long inclusiveDays =
                ChronoUnit.DAYS.between(
                        safeFrom,
                        safeTo
                )
                        + 1;


        if (
                inclusiveDays > 366
        ) {

            throw new IllegalArgumentException(
                    "Sales intelligence date range cannot exceed 366 days."
            );
        }


        LocalDate comparisonTo =
                safeFrom.minusDays(
                        1
                );

        LocalDate comparisonFrom =
                comparisonTo.minusDays(
                        inclusiveDays - 1
                );


        Aggregate current =
                loadAggregate(
                        safeFrom,
                        safeTo,
                        branchId
                );

        Aggregate previous =
                loadAggregate(
                        comparisonFrom,
                        comparisonTo,
                        branchId
                );


        BigDecimal averageOrderValue =
                current.orders == 0
                        ? ZERO
                        : current.revenue.divide(
                        BigDecimal.valueOf(
                                current.orders
                        ),
                        2,
                        RoundingMode.HALF_UP
                );


        SalesIntelligenceSummaryResponse summary =
                new SalesIntelligenceSummaryResponse(
                        current.revenue,
                        current.orders,
                        current.units,
                        current.customers,
                        averageOrderValue,
                        current.discount,
                        previous.revenue,
                        previous.orders,
                        percentChange(
                                current.revenue,
                                previous.revenue
                        ),
                        percentChange(
                                BigDecimal.valueOf(
                                        current.orders
                                ),
                                BigDecimal.valueOf(
                                        previous.orders
                                )
                        )
                );


        return new SalesIntelligenceResponse(
                safeFrom,
                safeTo,
                comparisonFrom,
                comparisonTo,
                branchId,
                summary,
                loadDailyTrend(
                        safeFrom,
                        safeTo,
                        branchId
                ),
                loadWeekdayPerformance(
                        safeFrom,
                        safeTo,
                        branchId
                ),
                loadCategoryContribution(
                        safeFrom,
                        safeTo,
                        branchId
                ),
                loadBranchMix(
                        safeFrom,
                        safeTo,
                        branchId
                ),
                loadPickupHours(
                        safeFrom,
                        safeTo,
                        branchId
                )
        );
    }


    private Aggregate loadAggregate(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        if (
                branchId == null
        ) {

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
                                    rs.getBigDecimal(
                                            "revenue"
                                    ),
                                    rs.getLong(
                                            "orders"
                                    ),
                                    rs.getLong(
                                            "units"
                                    ),
                                    rs.getLong(
                                            "customers"
                                    ),
                                    rs.getBigDecimal(
                                            "discount"
                                    )
                            ),
                    Date.valueOf(
                            fromDate
                    ),
                    Date.valueOf(
                            toDate
                    )
            );
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
                                rs.getBigDecimal(
                                        "revenue"
                                ),
                                rs.getLong(
                                        "orders"
                                ),
                                rs.getLong(
                                        "units"
                                ),
                                rs.getLong(
                                        "customers"
                                ),
                                rs.getBigDecimal(
                                        "discount"
                                )
                        ),
                Date.valueOf(
                        fromDate
                ),
                Date.valueOf(
                        toDate
                ),
                branchId
        );
    }


    private List<SalesDailyPointResponse> loadDailyTrend(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        if (
                branchId == null
        ) {

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
                            new SalesDailyPointResponse(
                                    rs.getDate(
                                            "business_date"
                                    ).toLocalDate(),
                                    rs.getBigDecimal(
                                            "net_revenue"
                                    ),
                                    rs.getLong(
                                            "completed_orders"
                                    ),
                                    rs.getLong(
                                            "units_sold"
                                    ),
                                    rs.getLong(
                                            "unique_customers"
                                    )
                            ),
                    Date.valueOf(
                            fromDate
                    ),
                    Date.valueOf(
                            toDate
                    )
            );
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
                        new SalesDailyPointResponse(
                                rs.getDate(
                                        "business_date"
                                ).toLocalDate(),
                                rs.getBigDecimal(
                                        "net_revenue"
                                ),
                                rs.getLong(
                                        "completed_orders"
                                ),
                                rs.getLong(
                                        "units_sold"
                                ),
                                rs.getLong(
                                        "unique_customers"
                                )
                        ),
                Date.valueOf(
                        fromDate
                ),
                Date.valueOf(
                        toDate
                ),
                branchId
        );
    }


    private List<SalesWeekdayResponse> loadWeekdayPerformance(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        String table =
                branchId == null
                        ? "analytics_sales_daily"
                        : "analytics_branch_daily";


        String branchPredicate =
                branchId == null
                        ? ""
                        : " AND branch_id = ? ";


        String sql =
                """
                SELECT
                    EXTRACT(ISODOW FROM business_date)::INTEGER AS iso_day,
                    TRIM(TO_CHAR(business_date, 'Day')) AS weekday,
                    SUM(completed_orders) AS orders,
                    SUM(units_sold) AS units,
                    SUM(net_revenue) AS revenue,
                    COUNT(DISTINCT business_date) AS active_days
                FROM
                """
                        + table
                        + """
                 WHERE business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                GROUP BY
                    EXTRACT(ISODOW FROM business_date),
                    TRIM(TO_CHAR(business_date, 'Day'))
                ORDER BY iso_day ASC
                """;


        Object[] args =
                branchId == null
                        ? new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        )
                }
                        : new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                };


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {

                    BigDecimal revenue =
                            rs.getBigDecimal(
                                    "revenue"
                            );

                    long activeDays =
                            rs.getLong(
                                    "active_days"
                            );


                    BigDecimal average =
                            activeDays == 0
                                    ? ZERO
                                    : revenue.divide(
                                    BigDecimal.valueOf(
                                            activeDays
                                    ),
                                    2,
                                    RoundingMode.HALF_UP
                            );


                    return new SalesWeekdayResponse(
                            rs.getInt(
                                    "iso_day"
                            ),
                            rs.getString(
                                    "weekday"
                            ),
                            rs.getLong(
                                    "orders"
                            ),
                            rs.getLong(
                                    "units"
                            ),
                            revenue,
                            average
                    );
                },
                args
        );
    }


    private List<SalesCategoryResponse> loadCategoryContribution(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        String branchPredicate =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String sql =
                """
                WITH category_rollup AS (
                    SELECT
                        apd.category_id,
                        c.code,
                        c.name,
                        SUM(apd.order_count) AS order_count,
                        SUM(apd.quantity_sold) AS quantity_sold,
                        SUM(apd.gross_item_revenue) AS revenue
                    FROM analytics_product_daily apd
                    JOIN categories c
                        ON c.id = apd.category_id
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                    GROUP BY
                        apd.category_id,
                        c.code,
                        c.name
                ),
                total AS (
                    SELECT
                        COALESCE(SUM(revenue), 0) AS total_revenue
                    FROM category_rollup
                )
                SELECT
                    cr.category_id,
                    cr.code,
                    cr.name,
                    cr.order_count,
                    cr.quantity_sold,
                    cr.revenue,
                    CASE
                        WHEN total.total_revenue = 0
                            THEN 0
                        ELSE
                            ROUND(
                                (
                                    cr.revenue
                                    / total.total_revenue
                                    * 100
                                ),
                                2
                            )
                    END AS revenue_share
                FROM category_rollup cr
                CROSS JOIN total
                ORDER BY
                    cr.revenue DESC,
                    cr.category_id ASC
                """;


        Object[] args =
                branchId == null
                        ? new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        )
                }
                        : new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                };


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new SalesCategoryResponse(
                                rs.getLong(
                                        "category_id"
                                ),
                                rs.getString(
                                        "code"
                                ),
                                rs.getString(
                                        "name"
                                ),
                                rs.getLong(
                                        "order_count"
                                ),
                                rs.getLong(
                                        "quantity_sold"
                                ),
                                rs.getBigDecimal(
                                        "revenue"
                                ),
                                rs.getBigDecimal(
                                        "revenue_share"
                                )
                        ),
                args
        );
    }


    private List<SalesBranchMixResponse> loadBranchMix(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        String branchPredicate =
                branchId == null
                        ? ""
                        : " AND abd.branch_id = ? ";


        String sql =
                """
                WITH branch_rollup AS (
                    SELECT
                        abd.branch_id,
                        b.code,
                        b.name,
                        SUM(abd.completed_orders) AS orders,
                        SUM(abd.units_sold) AS units,
                        SUM(abd.net_revenue) AS revenue
                    FROM analytics_branch_daily abd
                    JOIN branches b
                        ON b.id = abd.branch_id
                    WHERE abd.business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                    GROUP BY
                        abd.branch_id,
                        b.code,
                        b.name
                ),
                total AS (
                    SELECT
                        COALESCE(SUM(revenue), 0) AS total_revenue
                    FROM branch_rollup
                )
                SELECT
                    br.branch_id,
                    br.code,
                    br.name,
                    br.orders,
                    br.units,
                    br.revenue,
                    CASE
                        WHEN total.total_revenue = 0
                            THEN 0
                        ELSE
                            ROUND(
                                (
                                    br.revenue
                                    / total.total_revenue
                                    * 100
                                ),
                                2
                            )
                    END AS revenue_share
                FROM branch_rollup br
                CROSS JOIN total
                ORDER BY
                    br.revenue DESC,
                    br.branch_id ASC
                """;


        Object[] args =
                branchId == null
                        ? new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        )
                }
                        : new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                };


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new SalesBranchMixResponse(
                                rs.getLong(
                                        "branch_id"
                                ),
                                rs.getString(
                                        "code"
                                ),
                                rs.getString(
                                        "name"
                                ),
                                rs.getLong(
                                        "orders"
                                ),
                                rs.getLong(
                                        "units"
                                ),
                                rs.getBigDecimal(
                                        "revenue"
                                ),
                                rs.getBigDecimal(
                                        "revenue_share"
                                )
                        ),
                args
        );
    }


    private List<SalesPickupHourResponse> loadPickupHours(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        String branchPredicate =
                branchId == null
                        ? ""
                        : " AND branch_id = ? ";


        String sql =
                """
                SELECT
                    pickup_hour,
                    SUM(completed_orders) AS orders,
                    SUM(units_sold) AS units,
                    SUM(net_revenue) AS revenue
                FROM analytics_sales_hourly
                WHERE business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                GROUP BY pickup_hour
                ORDER BY pickup_hour ASC
                """;


        Object[] args =
                branchId == null
                        ? new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        )
                }
                        : new Object[]{
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                };


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new SalesPickupHourResponse(
                                rs.getInt(
                                        "pickup_hour"
                                ),
                                rs.getLong(
                                        "orders"
                                ),
                                rs.getLong(
                                        "units"
                                ),
                                rs.getBigDecimal(
                                        "revenue"
                                )
                        ),
                args
        );
    }


    private BigDecimal percentChange(
            BigDecimal current,
            BigDecimal previous
    ) {

        if (
                previous == null
                        ||
                        previous.compareTo(
                                BigDecimal.ZERO
                        ) == 0
        ) {

            return current == null
                    ||
                    current.compareTo(
                            BigDecimal.ZERO
                    ) == 0
                    ? BigDecimal.ZERO
                    : new BigDecimal(
                    "100.00"
            );
        }


        return current
                .subtract(
                        previous
                )
                .divide(
                        previous,
                        6,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        new BigDecimal(
                                "100"
                        )
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    private record Aggregate(
            BigDecimal revenue,
            long orders,
            long units,
            long customers,
            BigDecimal discount
    ) {
    }
}
