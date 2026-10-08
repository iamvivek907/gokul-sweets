package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.DemandForecastItemResponse;
import com.gokulsweets.restaurant.reporting.dto.DemandForecastResponse;
import com.gokulsweets.restaurant.reporting.dto.DemandForecastSummaryResponse;
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
import java.util.*;

/** Coordinates demand forecast operations. */
@Service
@RequiredArgsConstructor
public class DemandForecastService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final int HISTORY_WEEKS = AppConstant.DEMAND_FORECAST_SERVICE_HISTORY_WEEKS;

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbcTemplate;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Forecasts the operation.
     *
     * @param targetDate the target date
     * @param branchId the branch id
     * @return the forecast result
     */
    @Transactional(readOnly = true)
    public DemandForecastResponse forecast(LocalDate targetDate, Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "forecast(LocalDate,Long)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REPORT_VIEW);
            if (targetDate == null) {
                throw new IllegalArgumentException("Target date is required.");
            }
            if (branchId == null || branchId <= 0) {
                throw new IllegalArgumentException("Branch is required.");
            }
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            if (targetDate.isBefore(today)) {
                throw new IllegalArgumentException(
                        "Demand forecast target date cannot be in the past.");
            }
            String branchName =
                    jdbcTemplate.query(
                            """
                            SELECT name
                            FROM branches
                            WHERE id = ?
                              AND active = TRUE
                            """,
                            rs -> rs.next() ? rs.getString("name") : null,
                            branchId);
            if (branchName == null) {
                throw new IllegalArgumentException("Active branch does not exist.");
            }
            LocalDate historyStart = targetDate.minusWeeks(HISTORY_WEEKS);
            LocalDate historyEnd = targetDate.minusWeeks(1);
            List<HistoryRow> historyRows =
                    jdbcTemplate.query(
                            """
                            SELECT
                                apd.business_date,
                                apd.product_id,
                                p.code AS product_code,
                                p.name AS product_name,
                                c.id AS category_id,
                                c.code AS category_code,
                                c.name AS category_name,
                                SUM(apd.quantity_sold) AS quantity_sold
                            FROM analytics_product_daily apd
                            JOIN products p
                                ON p.id = apd.product_id
                            JOIN categories c
                                ON c.id = p.category_id
                            WHERE
                                apd.branch_id = ?
                                AND apd.business_date BETWEEN ? AND ?
                                AND EXTRACT(
                                    ISODOW
                                    FROM apd.business_date
                                ) = ?
                            GROUP BY
                                apd.business_date,
                                apd.product_id,
                                p.code,
                                p.name,
                                c.id,
                                c.code,
                                c.name
                            ORDER BY
                                apd.product_id,
                                apd.business_date
                            """,
                            (rs, rowNum) ->
                                    new HistoryRow(
                                            rs.getDate("business_date").toLocalDate(),
                                            rs.getLong("product_id"),
                                            rs.getString("product_code"),
                                            rs.getString("product_name"),
                                            rs.getLong("category_id"),
                                            rs.getString("category_code"),
                                            rs.getString("category_name"),
                                            rs.getLong("quantity_sold")),
                            branchId,
                            Date.valueOf(historyStart),
                            Date.valueOf(historyEnd),
                            targetDate.getDayOfWeek().getValue());
            Map<Long, ProductHistory> productHistory = new LinkedHashMap<>();
            for (HistoryRow row : historyRows) {
                productHistory
                        .computeIfAbsent(
                                row.productId,
                                ignored ->
                                        new ProductHistory(
                                                row.productId,
                                                row.productCode,
                                                row.productName,
                                                row.categoryId,
                                                row.categoryCode,
                                                row.categoryName))
                        .quantities
                        .put(row.businessDate, row.quantitySold);
            }
            List<LocalDate> observationDates = new ArrayList<>();
            for (int week = HISTORY_WEEKS; week >= 1; week--) {
                observationDates.add(targetDate.minusWeeks(week));
            }
            List<DemandForecastItemResponse> forecasts =
                    productHistory.values().stream()
                            .map(history -> forecastProduct(history, observationDates))
                            .sorted(
                                    Comparator.comparingInt(
                                                    DemandForecastItemResponse::recommendedQuantity)
                                            .reversed()
                                            .thenComparing(DemandForecastItemResponse::productName))
                            .toList();
            long recommendedUnits =
                    forecasts.stream()
                            .mapToLong(DemandForecastItemResponse::recommendedQuantity)
                            .sum();
            long lowerUnits =
                    forecasts.stream().mapToLong(DemandForecastItemResponse::lowerQuantity).sum();
            long upperUnits =
                    forecasts.stream().mapToLong(DemandForecastItemResponse::upperQuantity).sum();
            DemandForecastSummaryResponse summary =
                    new DemandForecastSummaryResponse(
                            forecasts.size(),
                            recommendedUnits,
                            lowerUnits,
                            upperUnits,
                            forecasts.stream()
                                    .filter(
                                            item ->
                                                    item.confidence()
                                                            == DemandForecastConfidence.HIGH)
                                    .count(),
                            forecasts.stream()
                                    .filter(
                                            item ->
                                                    item.confidence()
                                                            == DemandForecastConfidence.MEDIUM)
                                    .count(),
                            forecasts.stream()
                                    .filter(
                                            item ->
                                                    item.confidence()
                                                            == DemandForecastConfidence.LOW)
                                    .count());
            return new DemandForecastResponse(
                    targetDate, branchId, branchName, HISTORY_WEEKS, summary, forecasts);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "forecast(LocalDate,Long)");
        }
    }

    /**
     * Forecasts product.
     *
     * @param history the history
     * @param observationDates the observation dates
     * @return the forecast product result
     */
    private DemandForecastItemResponse forecastProduct(
            ProductHistory history, List<LocalDate> observationDates) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DemandForecastService.class,
                        "forecastProduct(ProductHistory,List<LocalDate>)");
        try {
            List<Long> quantities =
                    observationDates.stream()
                            .map(date -> history.quantities.getOrDefault(date, 0L))
                            .toList();
            int weeksObserved = quantities.size();
            int weeksWithSales = (int) quantities.stream().filter(quantity -> quantity > 0).count();
            List<Long> recentFour =
                    quantities.subList(Math.max(0, quantities.size() - 4), quantities.size());
            List<Long> previousEight = quantities.subList(0, Math.max(0, quantities.size() - 4));
            BigDecimal recentAverage = average(recentFour);
            BigDecimal previousAverage = average(previousEight);
            BigDecimal weightedBase =
                    recentAverage
                            .multiply(new BigDecimal("0.65"))
                            .add(previousAverage.multiply(new BigDecimal("0.35")));
            BigDecimal rawTrend =
                    previousAverage.compareTo(BigDecimal.ZERO) == 0
                            ? ZERO
                            : recentAverage
                                    .subtract(previousAverage)
                                    .divide(previousAverage, 6, RoundingMode.HALF_UP);
            BigDecimal cappedTrend =
                    rawTrend.max(new BigDecimal("-0.30")).min(new BigDecimal("0.30"));
            BigDecimal adjustedForecast =
                    weightedBase.multiply(
                            BigDecimal.ONE.add(cappedTrend.multiply(new BigDecimal("0.50"))));
            BigDecimal standardDeviation = standardDeviation(quantities);
            BigDecimal mean = average(quantities);
            BigDecimal coefficientOfVariation =
                    mean.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ONE
                            : standardDeviation.divide(mean, 6, RoundingMode.HALF_UP);
            DemandForecastConfidence confidence =
                    confidence(weeksWithSales, coefficientOfVariation);
            BigDecimal safetyMultiplier =
                    switch (confidence) {
                        case HIGH -> new BigDecimal("0.60");
                        case MEDIUM -> new BigDecimal("0.85");
                        case LOW -> new BigDecimal("1.15");
                    };
            BigDecimal spread = standardDeviation.multiply(safetyMultiplier);
            int recommended = roundUp(adjustedForecast);
            int lower = roundDownNonNegative(adjustedForecast.subtract(spread));
            int upper = Math.max(recommended, roundUp(adjustedForecast.add(spread)));
            BigDecimal trendPercent =
                    rawTrend.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal variabilityPercent =
                    coefficientOfVariation
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
            String explanation =
                    explanation(
                            recentAverage,
                            previousAverage,
                            trendPercent,
                            weeksWithSales,
                            confidence);
            return new DemandForecastItemResponse(
                    history.productId,
                    history.productCode,
                    history.productName,
                    history.categoryId,
                    history.categoryCode,
                    history.categoryName,
                    recommended,
                    lower,
                    upper,
                    recentAverage,
                    previousAverage,
                    trendPercent,
                    variabilityPercent,
                    weeksObserved,
                    weeksWithSales,
                    confidence,
                    explanation);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "forecastProduct(ProductHistory,List<LocalDate>)");
        }
    }

    /**
     * Confidences the operation.
     *
     * @param weeksWithSales the weeks with sales
     * @param coefficientOfVariation the coefficient of variation
     * @return the confidence result
     */
    private DemandForecastConfidence confidence(
            int weeksWithSales, BigDecimal coefficientOfVariation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "confidence(int,BigDecimal)");
        try {
            if (weeksWithSales >= 8
                    && coefficientOfVariation.compareTo(new BigDecimal("0.35")) <= 0) {
                return DemandForecastConfidence.HIGH;
            }
            if (weeksWithSales >= 4
                    && coefficientOfVariation.compareTo(new BigDecimal("0.75")) <= 0) {
                return DemandForecastConfidence.MEDIUM;
            }
            return DemandForecastConfidence.LOW;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "confidence(int,BigDecimal)");
        }
    }

    /**
     * Explanations the operation.
     *
     * @param recentAverage the recent average
     * @param previousAverage the previous average
     * @param trendPercent the trend percent
     * @param weeksWithSales the weeks with sales
     * @param confidence the confidence
     * @return the explanation result
     */
    private String explanation(
            BigDecimal recentAverage,
            BigDecimal previousAverage,
            BigDecimal trendPercent,
            int weeksWithSales,
            DemandForecastConfidence confidence) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DemandForecastService.class,
                        "explanation(BigDecimal,BigDecimal,BigDecimal,int,DemandForecastConfidence)");
        try {
            return "Based on the same weekday over the previous 12 weeks. "
                    + "Recent 4-week average is "
                    + recentAverage.setScale(2, RoundingMode.HALF_UP)
                    + " units versus "
                    + previousAverage.setScale(2, RoundingMode.HALF_UP)
                    + " units across the earlier 8 weeks. "
                    + "Observed trend is "
                    + trendPercent
                    + "%, with sales present in "
                    + weeksWithSales
                    + " of 12 comparable weeks. "
                    + "Confidence: "
                    + confidence.name()
                    + ".";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "explanation(BigDecimal,BigDecimal,BigDecimal,int,DemandForecastConfidence)");
        }
    }

    /**
     * Averages the operation.
     *
     * @param values the values
     * @return the average result
     */
    private BigDecimal average(List<Long> values) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "average(List<Long>)");
        try {
            if (values.isEmpty()) {
                return ZERO;
            }
            long sum = values.stream().mapToLong(Long::longValue).sum();
            return BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DemandForecastService.class, "average(List<Long>)");
        }
    }

    /**
     * Standards deviation.
     *
     * @param values the values
     * @return the standard deviation result
     */
    private BigDecimal standardDeviation(List<Long> values) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "standardDeviation(List<Long>)");
        try {
            if (values.size() <= 1) {
                return ZERO;
            }
            double mean = values.stream().mapToDouble(Long::doubleValue).average().orElse(0.0);
            double variance =
                    values.stream()
                            .mapToDouble(
                                    value -> {
                                        double difference = value - mean;
                                        return difference * difference;
                                    })
                            .average()
                            .orElse(0.0);
            return BigDecimal.valueOf(Math.sqrt(variance)).setScale(4, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "standardDeviation(List<Long>)");
        }
    }

    /**
     * Rounds up.
     *
     * @param value the value
     * @return the round up result
     */
    private int roundUp(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "roundUp(BigDecimal)");
        try {
            return Math.max(0, value.setScale(0, RoundingMode.CEILING).intValue());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DemandForecastService.class, "roundUp(BigDecimal)");
        }
    }

    /**
     * Rounds down non negative.
     *
     * @param value the value
     * @return the round down non negative result
     */
    private int roundDownNonNegative(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastService.class, "roundDownNonNegative(BigDecimal)");
        try {
            return Math.max(0, value.setScale(0, RoundingMode.FLOOR).intValue());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastService.class,
                    "roundDownNonNegative(BigDecimal)");
        }
    }

    /** Immutable history row data contract. */
    private record HistoryRow(
            LocalDate businessDate,
            Long productId,
            String productCode,
            String productName,
            Long categoryId,
            String categoryCode,
            String categoryName,
            long quantitySold) {}

    /** Backend product history contract and implementation. */
    private static final class ProductHistory {

        private final Long productId;

        private final String productCode;

        private final String productName;

        private final Long categoryId;

        private final String categoryCode;

        private final String categoryName;

        private final Map<LocalDate, Long> quantities = new HashMap<>();

        private ProductHistory(
                Long productId,
                String productCode,
                String productName,
                Long categoryId,
                String categoryCode,
                String categoryName) {
            this.productId = productId;
            this.productCode = productCode;
            this.productName = productName;
            this.categoryId = categoryId;
            this.categoryCode = categoryCode;
            this.categoryName = categoryName;
        }
    }
}
