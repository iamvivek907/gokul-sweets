package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.ProductIntelligenceItemResponse;
import com.gokulsweets.restaurant.reporting.dto.ProductIntelligenceResponse;
import com.gokulsweets.restaurant.reporting.dto.ProductIntelligenceSummaryResponse;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductIntelligenceService {

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
    public ProductIntelligenceResponse getProductIntelligence(
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
                    "Product intelligence date range cannot exceed 366 days."
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


        long totalCompletedOrders =
                loadCompletedOrders(
                        safeFrom,
                        safeTo,
                        branchId
                );


        List<ProductRow> rows =
                loadProducts(
                        safeFrom,
                        safeTo,
                        comparisonFrom,
                        comparisonTo,
                        branchId
                );


        List<ProductIntelligenceItemResponse> products =
                new ArrayList<>();


        for (
                ProductRow row
                : rows
        ) {

            BigDecimal penetration =
                    percent(
                            BigDecimal.valueOf(
                                    row.orderCount
                            ),
                            BigDecimal.valueOf(
                                    totalCompletedOrders
                            )
                    );


            BigDecimal consistency =
                    percent(
                            BigDecimal.valueOf(
                                    row.activeSalesDays
                            ),
                            BigDecimal.valueOf(
                                    inclusiveDays
                            )
                    );


            BigDecimal growth =
                    growthPercent(
                            row.revenue,
                            row.previousRevenue
                    );


            Classification classification =
                    classify(
                            row,
                            penetration,
                            consistency,
                            growth
                    );


            products.add(
                    new ProductIntelligenceItemResponse(
                            row.productId,
                            row.productCode,
                            row.productName,
                            row.categoryId,
                            row.categoryCode,
                            row.categoryName,
                            row.orderCount,
                            row.quantitySold,
                            row.revenue,
                            row.uniqueCustomers,
                            row.activeSalesDays,
                            penetration,
                            consistency,
                            row.previousRevenue,
                            row.previousQuantity,
                            growth,
                            classification.state,
                            classification.reason
                    )
            );
        }


        long starCount =
                products.stream()
                        .filter(
                                product ->
                                        product.state()
                                                == ProductPerformanceState.STAR
                        )
                        .count();


        long growingCount =
                products.stream()
                        .filter(
                                product ->
                                        product.state()
                                                == ProductPerformanceState.GROWING
                        )
                        .count();


        long decliningCount =
                products.stream()
                        .filter(
                                product ->
                                        product.state()
                                                == ProductPerformanceState.DECLINING
                        )
                        .count();


        long totalQuantity =
                products.stream()
                        .mapToLong(
                                ProductIntelligenceItemResponse::quantitySold
                        )
                        .sum();


        BigDecimal totalRevenue =
                products.stream()
                        .map(
                                ProductIntelligenceItemResponse::grossItemRevenue
                        )
                        .reduce(
                                ZERO,
                                BigDecimal::add
                        );


        ProductIntelligenceSummaryResponse summary =
                new ProductIntelligenceSummaryResponse(
                        products.stream()
                                .filter(
                                        product ->
                                                product.quantitySold() > 0
                                )
                                .count(),
                        totalQuantity,
                        totalRevenue,
                        starCount,
                        growingCount,
                        decliningCount
                );


        return new ProductIntelligenceResponse(
                safeFrom,
                safeTo,
                comparisonFrom,
                comparisonTo,
                branchId,
                summary,
                products
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


    private List<ProductRow> loadProducts(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate comparisonFrom,
            LocalDate comparisonTo,
            Long branchId
    ) {

        String currentBranchPredicate =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String previousBranchPredicate =
                branchId == null
                        ? ""
                        : " AND apd.branch_id = ? ";


        String sql =
                """
                WITH current_period AS (
                    SELECT
                        apd.product_id,
                        SUM(apd.order_count) AS order_count,
                        SUM(apd.quantity_sold) AS quantity_sold,
                        SUM(apd.gross_item_revenue) AS revenue,
                        SUM(apd.unique_customers) AS unique_customers,
                        COUNT(DISTINCT apd.business_date) AS active_sales_days
                    FROM analytics_product_daily apd
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + currentBranchPredicate
                        + """
                    GROUP BY apd.product_id
                ),
                previous_period AS (
                    SELECT
                        apd.product_id,
                        SUM(apd.quantity_sold) AS quantity_sold,
                        SUM(apd.gross_item_revenue) AS revenue
                    FROM analytics_product_daily apd
                    WHERE apd.business_date BETWEEN ? AND ?
                """
                        + previousBranchPredicate
                        + """
                    GROUP BY apd.product_id
                )
                SELECT
                    p.id AS product_id,
                    p.code AS product_code,
                    p.name AS product_name,
                    c.id AS category_id,
                    c.code AS category_code,
                    c.name AS category_name,
                    COALESCE(cp.order_count, 0) AS order_count,
                    COALESCE(cp.quantity_sold, 0) AS quantity_sold,
                    COALESCE(cp.revenue, 0) AS revenue,
                    COALESCE(cp.unique_customers, 0) AS unique_customers,
                    COALESCE(cp.active_sales_days, 0) AS active_sales_days,
                    COALESCE(pp.quantity_sold, 0) AS previous_quantity_sold,
                    COALESCE(pp.revenue, 0) AS previous_revenue
                FROM products p
                JOIN categories c
                    ON c.id = p.category_id
                LEFT JOIN current_period cp
                    ON cp.product_id = p.id
                LEFT JOIN previous_period pp
                    ON pp.product_id = p.id
                WHERE
                    p.active = TRUE
                    AND c.active = TRUE
                ORDER BY
                    revenue DESC,
                    quantity_sold DESC,
                    p.name ASC,
                    p.id ASC
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
                                    comparisonFrom
                            ),
                            Date.valueOf(
                                    comparisonTo
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
                                    comparisonFrom
                            ),
                            Date.valueOf(
                                    comparisonTo
                            ),
                            branchId
                    };
        }


        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        new ProductRow(
                                rs.getLong(
                                        "product_id"
                                ),
                                rs.getString(
                                        "product_code"
                                ),
                                rs.getString(
                                        "product_name"
                                ),
                                rs.getLong(
                                        "category_id"
                                ),
                                rs.getString(
                                        "category_code"
                                ),
                                rs.getString(
                                        "category_name"
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
                                rs.getLong(
                                        "unique_customers"
                                ),
                                rs.getInt(
                                        "active_sales_days"
                                ),
                                rs.getLong(
                                        "previous_quantity_sold"
                                ),
                                rs.getBigDecimal(
                                        "previous_revenue"
                                )
                        ),
                args
        );
    }


    private Classification classify(
            ProductRow row,
            BigDecimal penetration,
            BigDecimal consistency,
            BigDecimal growth
    ) {

        if (
                row.orderCount < 3
                        ||
                        row.activeSalesDays < 2
        ) {

            return new Classification(
                    ProductPerformanceState.NEW_INSUFFICIENT_DATA,
                    "Not enough completed-order history in the selected period for a stable classification."
            );
        }


        if (
                row.previousRevenue.compareTo(
                        BigDecimal.ZERO
                ) > 0
                        &&
                        growth.compareTo(
                                new BigDecimal(
                                        "-20.00"
                                )
                        ) <= 0
        ) {

            return new Classification(
                    ProductPerformanceState.DECLINING,
                    "Revenue is at least 20% below the previous comparable period."
            );
        }


        if (
                penetration.compareTo(
                        new BigDecimal(
                                "20.00"
                        )
                ) >= 0
                        &&
                        consistency.compareTo(
                                new BigDecimal(
                                        "50.00"
                                )
                        ) >= 0
                        &&
                        growth.compareTo(
                                BigDecimal.ZERO
                        ) >= 0
        ) {

            return new Classification(
                    ProductPerformanceState.STAR,
                    "High order penetration, consistent selling days, and non-negative period growth."
            );
        }


        if (
                growth.compareTo(
                        new BigDecimal(
                                "20.00"
                        )
                ) >= 0
        ) {

            return new Classification(
                    ProductPerformanceState.GROWING,
                    "Revenue is at least 20% above the previous comparable period."
            );
        }


        if (
                penetration.compareTo(
                        new BigDecimal(
                                "10.00"
                        )
                ) >= 0
                        &&
                        consistency.compareTo(
                                new BigDecimal(
                                        "40.00"
                                )
                        ) >= 0
        ) {

            return new Classification(
                    ProductPerformanceState.CORE,
                    "Meaningful order penetration with recurring sales across the selected period."
            );
        }


        return new Classification(
                ProductPerformanceState.NICHE,
                "The product sells, but its penetration or selling-day consistency is below the current core thresholds."
        );
    }


    private BigDecimal percent(
            BigDecimal numerator,
            BigDecimal denominator
    ) {

        if (
                denominator.compareTo(
                        BigDecimal.ZERO
                ) == 0
        ) {

            return ZERO;
        }


        return numerator
                .divide(
                        denominator,
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


    private BigDecimal growthPercent(
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


    private record ProductRow(
            Long productId,
            String productCode,
            String productName,
            Long categoryId,
            String categoryCode,
            String categoryName,
            long orderCount,
            long quantitySold,
            BigDecimal revenue,
            long uniqueCustomers,
            int activeSalesDays,
            long previousQuantity,
            BigDecimal previousRevenue
    ) {
    }


    private record Classification(
            ProductPerformanceState state,
            String reason
    ) {
    }
}
