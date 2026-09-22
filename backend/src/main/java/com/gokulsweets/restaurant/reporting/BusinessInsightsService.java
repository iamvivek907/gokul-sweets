package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.BusinessInsightResponse;
import com.gokulsweets.restaurant.reporting.dto.BusinessInsightsResponse;
import com.gokulsweets.restaurant.reporting.dto.BusinessInsightsSummaryResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessInsightsService {

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
    public BusinessInsightsResponse getInsights(
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
                    "Business insights date range cannot exceed 366 days."
            );
        }


        LocalDate previousTo =
                safeFrom.minusDays(
                        1
                );

        LocalDate previousFrom =
                previousTo.minusDays(
                        inclusiveDays - 1
                );


        List<BusinessInsightResponse> insights =
                new ArrayList<>();


        addSalesInsight(
                insights,
                safeFrom,
                safeTo,
                previousFrom,
                previousTo,
                branchId
        );


        addProductInsights(
                insights,
                safeFrom,
                safeTo,
                previousFrom,
                previousTo,
                branchId
        );


        addCustomerInsights(
                insights
        );


        addBasketInsight(
                insights,
                safeFrom,
                safeTo,
                branchId
        );


        addDemandVolatilityInsights(
                insights,
                safeFrom,
                safeTo,
                branchId
        );


        if (
                branchId == null
        ) {

            addBranchConcentrationInsight(
                    insights,
                    safeFrom,
                    safeTo
            );
        }


        insights.sort(
                Comparator
                        .comparingInt(
                                (
                                        BusinessInsightResponse insight
                                ) ->
                                        severityRank(
                                                insight.severity()
                                        )
                        )
                        .thenComparing(
                                BusinessInsightResponse::title
                        )
        );


        long actionCount =
                insights.stream()
                        .filter(
                                insight ->
                                        insight.severity()
                                                == BusinessInsightSeverity.ACTION
                        )
                        .count();


        long watchCount =
                insights.stream()
                        .filter(
                                insight ->
                                        insight.severity()
                                                == BusinessInsightSeverity.WATCH
                        )
                        .count();


        long infoCount =
                insights.stream()
                        .filter(
                                insight ->
                                        insight.severity()
                                                == BusinessInsightSeverity.INFO
                        )
                        .count();


        return new BusinessInsightsResponse(
                safeFrom,
                safeTo,
                branchId,
                new BusinessInsightsSummaryResponse(
                        insights.size(),
                        actionCount,
                        watchCount,
                        infoCount
                ),
                insights
        );
    }


    private void addSalesInsight(
            List<BusinessInsightResponse> insights,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFrom,
            LocalDate previousTo,
            Long branchId
    ) {

        BigDecimal current =
                loadRevenue(
                        fromDate,
                        toDate,
                        branchId
                );


        BigDecimal previous =
                loadRevenue(
                        previousFrom,
                        previousTo,
                        branchId
                );


        BigDecimal change =
                percentChange(
                        current,
                        previous
                );


        if (
                previous.compareTo(
                        BigDecimal.ZERO
                ) == 0
        ) {

            return;
        }


        if (
                change.compareTo(
                        new BigDecimal(
                                "-15.00"
                        )
                ) <= 0
        ) {

            insights.add(
                    new BusinessInsightResponse(
                            BusinessInsightType.SALES_DROP,
                            change.compareTo(
                                    new BigDecimal(
                                            "-25.00"
                                    )
                            ) <= 0
                                    ? BusinessInsightSeverity.ACTION
                                    : BusinessInsightSeverity.WATCH,
                            "Revenue is down versus the previous comparable period",
                            "Completed-order revenue has fallen materially in the selected period.",
                            "Revenue changed by "
                                    + change
                                    + "% compared with "
                                    + previousFrom
                                    + " to "
                                    + previousTo
                                    + ".",
                            "BUSINESS",
                            null,
                            null,
                            change,
                            "Revenue change %"
                    )
            );

        } else if (
                change.compareTo(
                        new BigDecimal(
                                "20.00"
                        )
                ) >= 0
        ) {

            insights.add(
                    new BusinessInsightResponse(
                            BusinessInsightType.SALES_GROWTH,
                            BusinessInsightSeverity.INFO,
                            "Revenue is growing",
                            "Completed-order revenue is meaningfully above the previous comparable period.",
                            "Revenue changed by "
                                    + change
                                    + "% compared with "
                                    + previousFrom
                                    + " to "
                                    + previousTo
                                    + ".",
                            "BUSINESS",
                            null,
                            null,
                            change,
                            "Revenue change %"
                    )
            );
        }
    }


    private void addProductInsights(
            List<BusinessInsightResponse> insights,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFrom,
            LocalDate previousTo,
            Long branchId
    ) {

        String currentBranch =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String previousBranch =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String sql =
                """
                WITH current_period AS (
                    SELECT
                        apd.product_id,
                        SUM(apd.gross_item_revenue) AS revenue,
                        SUM(apd.quantity_sold) AS quantity
                    FROM analytics_product_daily apd
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + currentBranch
                        + """
                    GROUP BY apd.product_id
                ),
                previous_period AS (
                    SELECT
                        apd.product_id,
                        SUM(apd.gross_item_revenue) AS revenue,
                        SUM(apd.quantity_sold) AS quantity
                    FROM analytics_product_daily apd
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + previousBranch
                        + """
                    GROUP BY apd.product_id
                )
                SELECT
                    p.id,
                    p.name,
                    COALESCE(cp.revenue, 0) AS current_revenue,
                    COALESCE(pp.revenue, 0) AS previous_revenue,
                    COALESCE(cp.quantity, 0) AS current_quantity
                FROM products p
                LEFT JOIN current_period cp
                    ON cp.product_id = p.id
                LEFT JOIN previous_period pp
                    ON pp.product_id = p.id
                WHERE p.active = TRUE
                  AND (
                        COALESCE(cp.revenue, 0) > 0
                        OR COALESCE(pp.revenue, 0) > 0
                  )
                """;


        Object[] args;

        if (
                branchId == null
        ) {

            args =
                    new Object[]{
                            Date.valueOf(
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
                            ),
                            Date.valueOf(
                                    previousFrom
                            ),
                            Date.valueOf(
                                    previousTo
                            )
                    };

        } else {

            args =
                    new Object[]{
                            Date.valueOf(
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
                            ),
                            branchId,
                            Date.valueOf(
                                    previousFrom
                            ),
                            Date.valueOf(
                                    previousTo
                            ),
                            branchId
                    };
        }


        jdbcTemplate.query(
                sql,
                rs -> {

                    BigDecimal current =
                            rs.getBigDecimal(
                                    "current_revenue"
                            );

                    BigDecimal previous =
                            rs.getBigDecimal(
                                    "previous_revenue"
                            );


                    if (
                            previous.compareTo(
                                    BigDecimal.ZERO
                            ) == 0
                    ) {

                        return;
                    }


                    BigDecimal change =
                            percentChange(
                                    current,
                                    previous
                            );


                    Long productId =
                            rs.getLong(
                                    "id"
                            );

                    String productName =
                            rs.getString(
                                    "name"
                            );


                    if (
                            change.compareTo(
                                    new BigDecimal(
                                            "-25.00"
                                    )
                            ) <= 0
                    ) {

                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.PRODUCT_DECLINE,
                                        change.compareTo(
                                                new BigDecimal(
                                                        "-40.00"
                                                )
                                        ) <= 0
                                                ? BusinessInsightSeverity.ACTION
                                                : BusinessInsightSeverity.WATCH,
                                        productName + " is declining",
                                        "This product is generating materially less completed-order revenue than in the previous comparable period.",
                                        "Revenue changed by "
                                                + change
                                                + "%.",
                                        "PRODUCT",
                                        productId,
                                        productName,
                                        change,
                                        "Revenue change %"
                                )
                        );

                    } else if (
                            change.compareTo(
                                    new BigDecimal(
                                            "30.00"
                                    )
                            ) >= 0
                    ) {

                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.PRODUCT_GROWTH,
                                        BusinessInsightSeverity.INFO,
                                        productName + " is growing",
                                        "This product has strong positive revenue momentum versus the previous comparable period.",
                                        "Revenue changed by "
                                                + change
                                                + "%.",
                                        "PRODUCT",
                                        productId,
                                        productName,
                                        change,
                                        "Revenue change %"
                                )
                        );
                    }
                },
                args
        );
    }


    private void addCustomerInsights(
            List<BusinessInsightResponse> insights
    ) {

        jdbcTemplate.query(
                """
                SELECT
                    COUNT(*) FILTER (
                        WHERE
                            expected_purchase_gap_days IS NOT NULL
                            AND days_since_last_purchase >= expected_purchase_gap_days * 2.5
                            AND days_since_last_purchase < expected_purchase_gap_days * 4.0
                    ) AS at_risk,

                    COUNT(*) FILTER (
                        WHERE
                            expected_purchase_gap_days IS NOT NULL
                            AND days_since_last_purchase >= expected_purchase_gap_days * 4.0
                    ) AS lapsed,

                    COUNT(*) FILTER (
                        WHERE
                            expected_purchase_gap_days IS NOT NULL
                            AND expected_purchase_gap_days > 0
                            AND last_purchase_gap_days IS NOT NULL
                            AND last_purchase_gap_days >= expected_purchase_gap_days * 2.5
                            AND days_since_last_purchase <= expected_purchase_gap_days * 1.5
                            AND lifetime_orders >= 3
                    ) AS reactivated

                FROM analytics_customer_metrics
                """,
                rs -> {

                    if (
                            !rs.next()
                    ) {

                        return;
                    }


                    long atRisk =
                            rs.getLong(
                                    "at_risk"
                            );

                    long lapsed =
                            rs.getLong(
                                    "lapsed"
                            );

                    long reactivated =
                            rs.getLong(
                                    "reactivated"
                            );


                    if (
                            atRisk > 0
                    ) {

                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.CUSTOMER_AT_RISK,
                                        atRisk >= 10
                                                ? BusinessInsightSeverity.ACTION
                                                : BusinessInsightSeverity.WATCH,
                                        "Customers are taking longer than usual to return",
                                        "Some repeat customers have passed 2.5× their normal reorder interval.",
                                        atRisk
                                                + " customers currently meet the at-risk rule.",
                                        "CUSTOMER_SEGMENT",
                                        null,
                                        "At-risk customers",
                                        BigDecimal.valueOf(
                                                atRisk
                                        ),
                                        "Customers"
                                )
                        );
                    }


                    if (
                            lapsed > 0
                    ) {

                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.CUSTOMER_LAPSED,
                                        BusinessInsightSeverity.ACTION,
                                        "Lapsed repeat customers need attention",
                                        "These customers have gone beyond 4× their normal reorder interval.",
                                        lapsed
                                                + " customers currently meet the lapsed rule.",
                                        "CUSTOMER_SEGMENT",
                                        null,
                                        "Lapsed customers",
                                        BigDecimal.valueOf(
                                                lapsed
                                        ),
                                        "Customers"
                                )
                        );
                    }


                    if (
                            reactivated > 0
                    ) {

                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.CUSTOMER_REACTIVATED,
                                        BusinessInsightSeverity.INFO,
                                        "Previously lapsed customers are returning",
                                        "Some customers who had unusually long prior gaps are currently back within their normal purchase rhythm.",
                                        reactivated
                                                + " customers currently meet the reactivated rule.",
                                        "CUSTOMER_SEGMENT",
                                        null,
                                        "Reactivated customers",
                                        BigDecimal.valueOf(
                                                reactivated
                                        ),
                                        "Customers"
                                )
                        );
                    }
                }
        );
    }


    private void addBasketInsight(
            List<BusinessInsightResponse> insights,
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
                    COUNT(*) AS strong_pair_count
                FROM (
                    SELECT
                        product_a_id,
                        product_b_id,
                        SUM(pair_order_count) AS pair_orders
                    FROM analytics_product_pair_daily
                    WHERE business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                    GROUP BY
                        product_a_id,
                        product_b_id
                    HAVING SUM(pair_order_count) >= 3
                ) pairs
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


        Long pairCount =
                jdbcTemplate.queryForObject(
                        sql,
                        Long.class,
                        args
                );


        if (
                pairCount != null
                        &&
                        pairCount > 0
        ) {

            insights.add(
                    new BusinessInsightResponse(
                            BusinessInsightType.BASKET_OPPORTUNITY,
                            BusinessInsightSeverity.INFO,
                            "Repeat product-pair behavior is visible",
                            "Multiple product pairs appear together repeatedly and can be reviewed for combo or upsell opportunities.",
                            pairCount
                                    + " product pairs appeared together in at least 3 completed orders.",
                            "BASKET",
                            null,
                            "Product pairs",
                            BigDecimal.valueOf(
                                    pairCount
                            ),
                            "Repeat pairs"
                    )
            );
        }
    }


    private void addDemandVolatilityInsights(
            List<BusinessInsightResponse> insights,
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
                SELECT
                    p.id,
                    p.name,
                    AVG(apd.quantity_sold) AS avg_quantity,
                    STDDEV_POP(apd.quantity_sold) AS stddev_quantity,
                    COUNT(*) AS active_days
                FROM analytics_product_daily apd
                JOIN products p
                    ON p.id = apd.product_id
                WHERE apd.business_date BETWEEN ? AND ?
                """
                        + branchPredicate
                        + """
                GROUP BY
                    p.id,
                    p.name
                HAVING COUNT(*) >= 4
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


        jdbcTemplate.query(
                sql,
                rs -> {

                    BigDecimal average =
                            rs.getBigDecimal(
                                    "avg_quantity"
                            );

                    BigDecimal stddev =
                            rs.getBigDecimal(
                                    "stddev_quantity"
                            );


                    if (
                            average == null
                                    ||
                                    stddev == null
                                    ||
                                    average.compareTo(
                                            BigDecimal.ZERO
                                    ) == 0
                    ) {

                        return;
                    }


                    BigDecimal variability =
                            stddev
                                    .divide(
                                            average,
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


                    if (
                            variability.compareTo(
                                    new BigDecimal(
                                            "80.00"
                                    )
                            ) >= 0
                    ) {

                        Long productId =
                                rs.getLong(
                                        "id"
                                );

                        String productName =
                                rs.getString(
                                        "name"
                                );


                        insights.add(
                                new BusinessInsightResponse(
                                        BusinessInsightType.DEMAND_VOLATILITY,
                                        BusinessInsightSeverity.WATCH,
                                        productName + " has volatile demand",
                                        "Daily quantities vary widely, so production planning should use a wider safety range.",
                                        "Observed quantity variability is "
                                                + variability
                                                + "% across active sales days.",
                                        "PRODUCT",
                                        productId,
                                        productName,
                                        variability,
                                        "Demand variability %"
                                )
                        );
                    }
                },
                args
        );
    }


    private void addBranchConcentrationInsight(
            List<BusinessInsightResponse> insights,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        List<BranchShareRow> branches =
                jdbcTemplate.query(
                        """
                        WITH branch_totals AS (
                            SELECT
                                abd.branch_id,
                                b.name,
                                SUM(abd.net_revenue) AS revenue
                            FROM analytics_branch_daily abd
                            JOIN branches b
                                ON b.id = abd.branch_id
                            WHERE abd.business_date BETWEEN ? AND ?
                            GROUP BY
                                abd.branch_id,
                                b.name
                        ),
                        total AS (
                            SELECT
                                COALESCE(
                                    SUM(revenue),
                                    0
                                ) AS total_revenue
                            FROM branch_totals
                        )
                        SELECT
                            bt.branch_id,
                            bt.name,
                            bt.revenue,
                            CASE
                                WHEN total.total_revenue = 0
                                    THEN 0
                                ELSE
                                    ROUND(
                                        bt.revenue
                                        / total.total_revenue
                                        * 100,
                                        2
                                    )
                            END AS revenue_share
                        FROM branch_totals bt
                        CROSS JOIN total
                        ORDER BY
                            bt.revenue DESC
                        LIMIT 1
                        """,
                        (rs, rowNum) ->
                                new BranchShareRow(
                                        rs.getLong(
                                                "branch_id"
                                        ),
                                        rs.getString(
                                                "name"
                                        ),
                                        rs.getBigDecimal(
                                                "revenue_share"
                                        )
                                ),
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        )
                );


        if (
                branches.isEmpty()
        ) {

            return;
        }


        BranchShareRow top =
                branches.get(
                        0
                );


        if (
                top.revenueShare.compareTo(
                        new BigDecimal(
                                "75.00"
                        )
                ) >= 0
        ) {

            insights.add(
                    new BusinessInsightResponse(
                            BusinessInsightType.BRANCH_CONCENTRATION,
                            BusinessInsightSeverity.WATCH,
                            "Revenue is highly concentrated in one branch",
                            "A large share of selected-period revenue is coming from a single branch.",
                            top.branchName
                                    + " contributes "
                                    + top.revenueShare
                                    + "% of total branch revenue.",
                            "BRANCH",
                            top.branchId,
                            top.branchName,
                            top.revenueShare,
                            "Revenue share %"
                    )
            );
        }
    }


    private BigDecimal loadRevenue(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        if (
                branchId == null
        ) {

            BigDecimal value =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                COALESCE(
                                    SUM(net_revenue),
                                    0
                                )
                            FROM analytics_sales_daily
                            WHERE business_date BETWEEN ? AND ?
                            """,
                            BigDecimal.class,
                            Date.valueOf(
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
                            )
                    );

            return value == null
                    ? ZERO
                    : value;
        }


        BigDecimal value =
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                            COALESCE(
                                SUM(net_revenue),
                                0
                            )
                        FROM analytics_branch_daily
                        WHERE business_date BETWEEN ? AND ?
                          AND branch_id = ?
                        """,
                        BigDecimal.class,
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                );


        return value == null
                ? ZERO
                : value;
    }


    private BigDecimal percentChange(
            BigDecimal current,
            BigDecimal previous
    ) {

        if (
                previous.compareTo(
                        BigDecimal.ZERO
                ) == 0
        ) {

            return current.compareTo(
                    BigDecimal.ZERO
            ) == 0
                    ? ZERO
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


    private int severityRank(
            BusinessInsightSeverity severity
    ) {

        return switch (
                severity
                ) {

            case ACTION ->
                    0;

            case WATCH ->
                    1;

            case INFO ->
                    2;
        };
    }


    private record BranchShareRow(
            Long branchId,
            String branchName,
            BigDecimal revenueShare
    ) {
    }
}
