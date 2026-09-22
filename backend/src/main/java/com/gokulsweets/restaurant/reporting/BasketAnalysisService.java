package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.BasketAnalysisResponse;
import com.gokulsweets.restaurant.reporting.dto.BasketAnalysisSummaryResponse;
import com.gokulsweets.restaurant.reporting.dto.BasketPairResponse;
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
public class BasketAnalysisService {

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
    public BasketAnalysisResponse getBasketAnalysis(
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
                    "Basket analysis date range cannot exceed 366 days."
            );
        }


        long totalCompletedOrders =
                loadCompletedOrders(
                        safeFrom,
                        safeTo,
                        branchId
                );


        List<PairRow> rows =
                loadPairs(
                        safeFrom,
                        safeTo,
                        branchId
                );


        List<BasketPairResponse> pairs =
                rows.stream()
                        .map(
                                row ->
                                        toResponse(
                                                row,
                                                totalCompletedOrders
                                        )
                        )
                        .toList();


        long productsInOrders =
                loadProductsInOrders(
                        safeFrom,
                        safeTo,
                        branchId
                );


        BasketAnalysisSummaryResponse summary =
                new BasketAnalysisSummaryResponse(
                        totalCompletedOrders,
                        productsInOrders,
                        pairs.size(),
                        pairs.stream()
                                .filter(
                                        pair ->
                                                pair.strength()
                                                        == BasketPairStrength.STRONG
                                )
                                .count(),
                        pairs.stream()
                                .filter(
                                        pair ->
                                                pair.strength()
                                                        == BasketPairStrength.MODERATE
                                )
                                .count()
                );


        return new BasketAnalysisResponse(
                safeFrom,
                safeTo,
                branchId,
                summary,
                pairs
        );
    }


    private long loadCompletedOrders(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        if (
                branchId == null
        ) {

            Long value =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                COALESCE(
                                    SUM(completed_orders),
                                    0
                                )
                            FROM analytics_sales_daily
                            WHERE business_date BETWEEN ? AND ?
                            """,
                            Long.class,
                            Date.valueOf(
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
                            )
                    );

            return value == null
                    ? 0L
                    : value;
        }


        Long value =
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                            COALESCE(
                                SUM(completed_orders),
                                0
                            )
                        FROM analytics_branch_daily
                        WHERE business_date BETWEEN ? AND ?
                          AND branch_id = ?
                        """,
                        Long.class,
                        Date.valueOf(
                                fromDate
                        ),
                        Date.valueOf(
                                toDate
                        ),
                        branchId
                );


        return value == null
                ? 0L
                : value;
    }


    private long loadProductsInOrders(
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
                    COUNT(
                        DISTINCT product_id
                    )
                FROM analytics_product_daily
                WHERE business_date BETWEEN ? AND ?
                """
                        + branchPredicate;


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


        Long value =
                jdbcTemplate.queryForObject(
                        sql,
                        Long.class,
                        args
                );


        return value == null
                ? 0L
                : value;
    }


    private List<PairRow> loadPairs(
            LocalDate fromDate,
            LocalDate toDate,
            Long branchId
    ) {

        String branchPredicatePairs =
                branchId == null
                        ? ""
                        : " AND appd.branch_id = ? ";


        String branchPredicateProducts =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String sql =
                """
                WITH pair_rollup AS (
                    SELECT
                        appd.product_a_id,
                        appd.product_b_id,
                        SUM(
                            appd.pair_order_count
                        ) AS pair_order_count
                    FROM analytics_product_pair_daily appd
                    WHERE appd.business_date BETWEEN ? AND ?
                """
                        + branchPredicatePairs
                        + """
                    GROUP BY
                        appd.product_a_id,
                        appd.product_b_id
                ),
                product_rollup AS (
                    SELECT
                        apd.product_id,
                        SUM(
                            apd.order_count
                        ) AS order_count
                    FROM analytics_product_daily apd
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + branchPredicateProducts
                        + """
                    GROUP BY
                        apd.product_id
                )
                SELECT
                    pair_rollup.product_a_id,
                    pa.code AS product_a_code,
                    pa.name AS product_a_name,

                    pair_rollup.product_b_id,
                    pb.code AS product_b_code,
                    pb.name AS product_b_name,

                    pair_rollup.pair_order_count,

                    COALESCE(
                        product_a.order_count,
                        0
                    ) AS product_a_order_count,

                    COALESCE(
                        product_b.order_count,
                        0
                    ) AS product_b_order_count

                FROM pair_rollup

                JOIN products pa
                    ON pa.id = pair_rollup.product_a_id

                JOIN products pb
                    ON pb.id = pair_rollup.product_b_id

                LEFT JOIN product_rollup product_a
                    ON product_a.product_id = pair_rollup.product_a_id

                LEFT JOIN product_rollup product_b
                    ON product_b.product_id = pair_rollup.product_b_id

                WHERE pair_rollup.pair_order_count > 0

                ORDER BY
                    pair_rollup.pair_order_count DESC,
                    pa.name ASC,
                    pb.name ASC
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
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
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
                                    fromDate
                            ),
                            Date.valueOf(
                                    toDate
                            ),
                            branchId
                    };
        }


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new PairRow(
                                rs.getLong(
                                        "product_a_id"
                                ),
                                rs.getString(
                                        "product_a_code"
                                ),
                                rs.getString(
                                        "product_a_name"
                                ),
                                rs.getLong(
                                        "product_b_id"
                                ),
                                rs.getString(
                                        "product_b_code"
                                ),
                                rs.getString(
                                        "product_b_name"
                                ),
                                rs.getLong(
                                        "pair_order_count"
                                ),
                                rs.getLong(
                                        "product_a_order_count"
                                ),
                                rs.getLong(
                                        "product_b_order_count"
                                )
                        ),
                args
        );
    }


    private BasketPairResponse toResponse(
            PairRow row,
            long totalCompletedOrders
    ) {

        BigDecimal support =
                percent(
                        row.pairOrderCount,
                        totalCompletedOrders
                );


        BigDecimal confidenceAToB =
                percent(
                        row.pairOrderCount,
                        row.productAOrderCount
                );


        BigDecimal confidenceBToA =
                percent(
                        row.pairOrderCount,
                        row.productBOrderCount
                );


        BigDecimal probabilityB =
                ratio(
                        row.productBOrderCount,
                        totalCompletedOrders
                );


        BigDecimal confidenceAToBRatio =
                ratio(
                        row.pairOrderCount,
                        row.productAOrderCount
                );


        BigDecimal lift =
                probabilityB.compareTo(
                        BigDecimal.ZERO
                ) == 0
                        ? ZERO
                        : confidenceAToBRatio
                        .divide(
                                probabilityB,
                                4,
                                RoundingMode.HALF_UP
                        );


        BasketPairStrength strength =
                classify(
                        row.pairOrderCount,
                        support,
                        lift
                );


        return new BasketPairResponse(
                row.productAId,
                row.productACode,
                row.productAName,
                row.productBId,
                row.productBCode,
                row.productBName,
                row.pairOrderCount,
                row.productAOrderCount,
                row.productBOrderCount,
                totalCompletedOrders,
                support,
                confidenceAToB,
                confidenceBToA,
                lift,
                strength,
                explanation(
                        row,
                        support,
                        confidenceAToB,
                        confidenceBToA,
                        lift,
                        strength
                )
        );
    }


    private BasketPairStrength classify(
            long pairOrders,
            BigDecimal support,
            BigDecimal lift
    ) {

        if (
                pairOrders < 3
        ) {

            return BasketPairStrength.INSUFFICIENT_DATA;
        }


        if (
                support.compareTo(
                        new BigDecimal(
                                "10.00"
                        )
                ) >= 0
                        &&
                        lift.compareTo(
                                new BigDecimal(
                                        "1.50"
                                )
                        ) >= 0
        ) {

            return BasketPairStrength.STRONG;
        }


        if (
                support.compareTo(
                        new BigDecimal(
                                "5.00"
                        )
                ) >= 0
                        &&
                        lift.compareTo(
                                new BigDecimal(
                                        "1.10"
                                )
                        ) > 0
        ) {

            return BasketPairStrength.MODERATE;
        }


        return BasketPairStrength.WEAK;
    }


    private String explanation(
            PairRow row,
            BigDecimal support,
            BigDecimal confidenceAToB,
            BigDecimal confidenceBToA,
            BigDecimal lift,
            BasketPairStrength strength
    ) {

        if (
                strength
                        == BasketPairStrength.INSUFFICIENT_DATA
        ) {

            return "This pair has appeared in only "
                    + row.pairOrderCount
                    + " completed orders, so there is not enough evidence yet for a stable basket relationship.";
        }


        return row.productAName
                + " and "
                + row.productBName
                + " appear together in "
                + support
                + "% of completed orders. "
                + "When "
                + row.productAName
                + " is purchased, "
                + row.productBName
                + " is also present "
                + confidenceAToB
                + "% of the time; the reverse confidence is "
                + confidenceBToA
                + "%. Lift is "
                + lift
                + "×, where values above 1 indicate the pair occurs together more often than expected from their individual popularity.";
    }


    private BigDecimal percent(
            long numerator,
            long denominator
    ) {

        return ratio(
                numerator,
                denominator
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


    private BigDecimal ratio(
            long numerator,
            long denominator
    ) {

        if (
                denominator <= 0
        ) {

            return ZERO;
        }


        return BigDecimal.valueOf(
                        numerator
                )
                .divide(
                        BigDecimal.valueOf(
                                denominator
                        ),
                        6,
                        RoundingMode.HALF_UP
                );
    }


    private record PairRow(
            Long productAId,
            String productACode,
            String productAName,
            Long productBId,
            String productBCode,
            String productBName,
            long pairOrderCount,
            long productAOrderCount,
            long productBOrderCount
    ) {
    }
}
