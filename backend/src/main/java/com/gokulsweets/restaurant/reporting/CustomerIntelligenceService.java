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
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerIntelligenceService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private static final int MAX_PAGE_SIZE = 100;

    private static final BigDecimal ZERO =
            new BigDecimal(
                    "0.00"
            );

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public CustomerIntelligenceResponse getCustomerIntelligence(
            String search,
            CustomerLifecycleState lifecycle,
            CustomerValueSegment valueSegment,
            Integer page,
            Integer size
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.REPORT_VIEW
                );


        int safePage =
                page == null
                        ? 0
                        : Math.max(
                        page,
                        0
                );

        int safeSize =
                size == null
                        ? DEFAULT_PAGE_SIZE
                        : Math.clamp(
                        size,
                        1
                        ,
                        MAX_PAGE_SIZE
                );


        String safeSearch =
                search == null
                        ? ""
                        : search
                        .trim()
                        .toLowerCase();


        CustomerIntelligenceSummaryResponse summary =
                loadSummary();


        long totalElements =
                loadCount(
                        safeSearch,
                        lifecycle,
                        valueSegment
                );


        int totalPages =
                safeSize == 0
                        ? 0
                        : (
                        int
                        ) Math.ceil(
                        (
                                double
                                ) totalElements
                                / safeSize
                );


        List<CustomerIntelligenceItemResponse> customers =
                loadCustomers(
                        safeSearch,
                        lifecycle,
                        valueSegment,
                        safePage,
                        safeSize
                );


        return new CustomerIntelligenceResponse(
                summary,
                new CustomerIntelligencePageResponse(
                        customers,
                        safePage,
                        safeSize,
                        totalElements,
                        totalPages
                )
        );
    }


    private CustomerIntelligenceSummaryResponse loadSummary() {

        return jdbcTemplate.queryForObject(
                """
                WITH ranked AS (
                    SELECT
                        acm.*,

                        PERCENT_RANK()
                            OVER (
                                ORDER BY acm.lifetime_spend
                            ) AS spend_percentile

                    FROM analytics_customer_metrics acm
                ),
                classified AS (
                    SELECT
                        ranked.*,

                        CASE
                            WHEN
                                expected_purchase_gap_days IS NOT NULL
                                AND expected_purchase_gap_days > 0
                                AND last_purchase_gap_days IS NOT NULL
                                AND last_purchase_gap_days >= expected_purchase_gap_days * 2.5
                                AND days_since_last_purchase <= expected_purchase_gap_days * 1.5
                                AND lifetime_orders >= 3
                                THEN 'REACTIVATED'

                            WHEN lifetime_orders = 1
                                THEN 'NEW'

                            WHEN expected_purchase_gap_days IS NULL
                                THEN
                                    CASE
                                        WHEN days_since_last_purchase <= 30
                                            THEN 'ACTIVE'
                                        WHEN days_since_last_purchase <= 60
                                            THEN 'WATCH'
                                        WHEN days_since_last_purchase <= 120
                                            THEN 'AT_RISK'
                                        ELSE 'LAPSED'
                                    END

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 1.5
                                THEN 'ACTIVE'

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 2.5
                                THEN 'WATCH'

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 4.0
                                THEN 'AT_RISK'

                            ELSE 'LAPSED'
                        END AS lifecycle_state,

                        CASE
                            WHEN lifetime_orders = 1
                                THEN 'NEW'

                            WHEN
                                lifetime_orders >= 5
                                AND spend_percentile >= 0.90
                                THEN 'VIP'

                            WHEN spend_percentile >= 0.75
                                THEN 'HIGH_VALUE'

                            WHEN lifetime_orders >= 3
                                THEN 'REGULAR'

                            ELSE 'OCCASIONAL'
                        END AS value_segment

                    FROM ranked
                )
                SELECT
                    COUNT(*) AS customers_with_purchases,

                    COUNT(*)
                        FILTER (
                            WHERE lifetime_orders >= 2
                        ) AS repeat_customers,

                    COUNT(*)
                        FILTER (
                            WHERE cc.verification_status = 'VERIFIED'
                        ) AS verified_customers,

                    COUNT(*)
                        FILTER (
                            WHERE lifecycle_state IN (
                                'NEW',
                                'ACTIVE',
                                'REACTIVATED'
                            )
                        ) AS active_customers,

                    COUNT(*)
                        FILTER (
                            WHERE lifecycle_state = 'AT_RISK'
                        ) AS at_risk_customers,

                    COUNT(*)
                        FILTER (
                            WHERE lifecycle_state = 'LAPSED'
                        ) AS lapsed_customers,

                    COUNT(*)
                        FILTER (
                            WHERE lifecycle_state = 'REACTIVATED'
                        ) AS reactivated_customers,

                    COUNT(*)
                        FILTER (
                            WHERE value_segment = 'HIGH_VALUE'
                        ) AS high_value_customers,

                    COUNT(*)
                        FILTER (
                            WHERE value_segment = 'VIP'
                        ) AS vip_customers,

                    COALESCE(
                        AVG(lifetime_spend),
                        0
                    ) AS average_lifetime_spend

                FROM classified c
                JOIN customer_contacts cc
                    ON cc.id = c.customer_contact_id
                """,
                (rs, rowNum) -> {

                    long customerCount =
                            rs.getLong(
                                    "customers_with_purchases"
                            );

                    long repeatCustomers =
                            rs.getLong(
                                    "repeat_customers"
                            );


                    BigDecimal repeatPercent =
                            customerCount == 0
                                    ? ZERO
                                    : BigDecimal.valueOf(
                                            repeatCustomers
                                    )
                                    .divide(
                                            BigDecimal.valueOf(
                                                    customerCount
                                            ),
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


                    return new CustomerIntelligenceSummaryResponse(
                            customerCount,
                            repeatCustomers,
                            repeatPercent,
                            rs.getLong(
                                    "verified_customers"
                            ),
                            rs.getLong(
                                    "active_customers"
                            ),
                            rs.getLong(
                                    "at_risk_customers"
                            ),
                            rs.getLong(
                                    "lapsed_customers"
                            ),
                            rs.getLong(
                                    "reactivated_customers"
                            ),
                            rs.getLong(
                                    "high_value_customers"
                            ),
                            rs.getLong(
                                    "vip_customers"
                            ),
                            rs.getBigDecimal(
                                    "average_lifetime_spend"
                            )
                    );
                }
        );
    }


    private long loadCount(
            String search,
            CustomerLifecycleState lifecycle,
            CustomerValueSegment valueSegment
    ) {

        List<Long> values =
                jdbcTemplate.query(
                        buildQuery(
                                true
                        ),
                        (rs, rowNum) ->
                                rs.getLong(
                                        "total_count"
                                ),
                        search,
                        search,
                        search,
                        lifecycle == null
                                ? null
                                : lifecycle.name(),
                        lifecycle == null
                                ? null
                                : lifecycle.name(),
                        valueSegment == null
                                ? null
                                : valueSegment.name(),
                        valueSegment == null
                                ? null
                                : valueSegment.name()
                );


        return values.isEmpty()
                ? 0L
                : values.getFirst(
        );
    }


    private List<CustomerIntelligenceItemResponse> loadCustomers(
            String search,
            CustomerLifecycleState lifecycle,
            CustomerValueSegment valueSegment,
            int page,
            int size
    ) {

        return jdbcTemplate.query(
                buildQuery(
                        false
                ),
                (rs, rowNum) -> {

                    BigDecimal expectedGap =
                            rs.getBigDecimal(
                                    "expected_purchase_gap_days"
                            );

                    Integer daysSinceLast =
                            (
                                    Integer
                                    ) rs.getObject(
                                    "days_since_last_purchase"
                            );


                    BigDecimal gapRatio =
                            expectedGap == null
                                    ||
                                    expectedGap.compareTo(
                                            BigDecimal.ZERO
                                    ) == 0
                                    ||
                                    daysSinceLast == null
                                    ? null
                                    : BigDecimal.valueOf(
                                            daysSinceLast
                                    )
                                    .divide(
                                            expectedGap,
                                            2,
                                            RoundingMode.HALF_UP
                                    );


                    String lifecycleState =
                            rs.getString(
                                    "lifecycle_state"
                            );


                    return new CustomerIntelligenceItemResponse(
                            rs.getLong(
                                    "customer_id"
                            ),
                            rs.getString(
                                    "latest_name"
                            ),
                            rs.getString(
                                    "normalized_phone"
                            ),
                            rs.getString(
                                    "verification_status"
                            ),
                            rs.getTimestamp(
                                    "first_purchase_at"
                            ).toLocalDateTime(),
                            rs.getTimestamp(
                                    "last_purchase_at"
                            ).toLocalDateTime(),
                            rs.getLong(
                                    "lifetime_orders"
                            ),
                            rs.getBigDecimal(
                                    "lifetime_spend"
                            ),
                            rs.getBigDecimal(
                                    "average_order_value"
                            ),
                            rs.getLong(
                                    "orders_30d"
                            ),
                            rs.getLong(
                                    "orders_90d"
                            ),
                            rs.getLong(
                                    "orders_365d"
                            ),
                            rs.getBigDecimal(
                                    "spend_30d"
                            ),
                            rs.getBigDecimal(
                                    "spend_90d"
                            ),
                            rs.getBigDecimal(
                                    "spend_365d"
                            ),
                            daysSinceLast,
                            expectedGap,
                            (
                                    Integer
                                    ) rs.getObject(
                                    "last_purchase_gap_days"
                            ),
                            gapRatio,
                            CustomerLifecycleState.valueOf(
                                    lifecycleState
                            ),
                            CustomerValueSegment.valueOf(
                                    rs.getString(
                                            "value_segment"
                                    )
                            ),
                            lifecycleReason(
                                    CustomerLifecycleState.valueOf(
                                            lifecycleState
                                    ),
                                    gapRatio
                            )
                    );
                },
                search,
                search,
                search,
                lifecycle == null
                        ? null
                        : lifecycle.name(),
                lifecycle == null
                        ? null
                        : lifecycle.name(),
                valueSegment == null
                        ? null
                        : valueSegment.name(),
                valueSegment == null
                        ? null
                        : valueSegment.name(),
                size,
                page * size
        );
    }


    private String buildQuery(
            boolean countOnly
    ) {

        String select =
                countOnly
                        ? """
                          SELECT
                              COUNT(*) AS total_count
                          """
                        : """
                          SELECT
                              customer_id,
                              latest_name,
                              normalized_phone,
                              verification_status,
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
                              expected_purchase_gap_days,
                              last_purchase_gap_days,
                              lifecycle_state,
                              value_segment
                          """;


        String pagination =
                countOnly
                        ? ""
                        : """
                          ORDER BY
                              CASE lifecycle_state
                                  WHEN 'LAPSED' THEN 1
                                  WHEN 'AT_RISK' THEN 2
                                  WHEN 'WATCH' THEN 3
                                  WHEN 'REACTIVATED' THEN 4
                                  WHEN 'ACTIVE' THEN 5
                                  ELSE 6
                              END ASC,
                              lifetime_spend DESC,
                              customer_id DESC
                          LIMIT ?
                          OFFSET ?
                          """;


        return """
                WITH ranked AS (
                    SELECT
                        acm.*,

                        PERCENT_RANK()
                            OVER (
                                ORDER BY acm.lifetime_spend
                            ) AS spend_percentile

                    FROM analytics_customer_metrics acm
                ),
                classified AS (
                    SELECT
                        ranked.*,

                        CASE
                            WHEN
                                expected_purchase_gap_days IS NOT NULL
                                AND expected_purchase_gap_days > 0
                                AND last_purchase_gap_days IS NOT NULL
                                AND last_purchase_gap_days >= expected_purchase_gap_days * 2.5
                                AND days_since_last_purchase <= expected_purchase_gap_days * 1.5
                                AND lifetime_orders >= 3
                                THEN 'REACTIVATED'

                            WHEN lifetime_orders = 1
                                THEN 'NEW'

                            WHEN expected_purchase_gap_days IS NULL
                                THEN
                                    CASE
                                        WHEN days_since_last_purchase <= 30
                                            THEN 'ACTIVE'
                                        WHEN days_since_last_purchase <= 60
                                            THEN 'WATCH'
                                        WHEN days_since_last_purchase <= 120
                                            THEN 'AT_RISK'
                                        ELSE 'LAPSED'
                                    END

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 1.5
                                THEN 'ACTIVE'

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 2.5
                                THEN 'WATCH'

                            WHEN days_since_last_purchase < expected_purchase_gap_days * 4.0
                                THEN 'AT_RISK'

                            ELSE 'LAPSED'
                        END AS lifecycle_state,

                        CASE
                            WHEN lifetime_orders = 1
                                THEN 'NEW'

                            WHEN
                                lifetime_orders >= 5
                                AND spend_percentile >= 0.90
                                THEN 'VIP'

                            WHEN spend_percentile >= 0.75
                                THEN 'HIGH_VALUE'

                            WHEN lifetime_orders >= 3
                                THEN 'REGULAR'

                            ELSE 'OCCASIONAL'
                        END AS value_segment

                    FROM ranked
                ),
                customer_rows AS (
                    SELECT
                        cc.id AS customer_id,
                        cc.latest_name,
                        cc.normalized_phone,
                        cc.verification_status,

                        c.first_purchase_at,
                        c.last_purchase_at,
                        c.lifetime_orders,
                        c.lifetime_spend,
                        c.average_order_value,
                        c.orders_30d,
                        c.orders_90d,
                        c.orders_365d,
                        c.spend_30d,
                        c.spend_90d,
                        c.spend_365d,
                        c.days_since_last_purchase,
                        c.expected_purchase_gap_days,
                        c.last_purchase_gap_days,
                        c.lifecycle_state,
                        c.value_segment

                    FROM classified c
                    JOIN customer_contacts cc
                        ON cc.id = c.customer_contact_id

                    WHERE
                        (
                            ? = ''
                            OR LOWER(
                                COALESCE(
                                    cc.latest_name,
                                    ''
                                )
                            ) LIKE '%' || ? || '%'
                            OR cc.normalized_phone LIKE '%' || ? || '%'
                        )

                        AND (
                            CAST(? AS VARCHAR) IS NULL
                            OR c.lifecycle_state = CAST(? AS VARCHAR)
                        )

                        AND (
                            CAST(? AS VARCHAR) IS NULL
                            OR c.value_segment = CAST(? AS VARCHAR)
                        )
                )
                """
                + select
                + """
                  FROM customer_rows
                  """
                + pagination;
    }


    private String lifecycleReason(
            CustomerLifecycleState state,
            BigDecimal gapRatio
    ) {

        return switch (
                state
                ) {

            case NEW ->
                    "Only one completed purchase is available, so a personal reorder rhythm is not established yet.";

            case ACTIVE ->
                    gapRatio == null
                            ? "The customer purchased recently and does not yet have enough history for a stable reorder interval."
                            : "The time since the last purchase is still within the customer’s normal reorder rhythm.";

            case WATCH ->
                    "The customer is taking longer than usual to return, but has not yet crossed the at-risk threshold.";

            case AT_RISK ->
                    "The current gap is materially longer than this customer’s usual reorder interval.";

            case LAPSED ->
                    "The customer has gone far beyond their usual reorder interval.";

            case REACTIVATED ->
                    "The customer returned after an unusually long previous gap and is currently back within their normal rhythm.";
        };
    }
}
