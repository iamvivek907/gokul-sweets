package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Versioned pilot assumptions. A missing or stale cost input fails closed before payment. */
@Service
public class DeliveryEconomicsService {

    private final EnhancementProperties flags;

    private final java.time.Clock clock;

    @Value("${delivery.economics.cost-valid-until:}")
    private String costValidUntil;

    @Value("${delivery.economics.version:}")
    private String version;

    @Value("${delivery.economics.food-cost-rate:0}")
    private BigDecimal foodRate;

    @Value("${delivery.economics.packaging-cost:0}")
    private BigDecimal packaging;

    @Value("${delivery.economics.labour-cost:0}")
    private BigDecimal labour;

    @Value("${delivery.economics.waste-cost:0}")
    private BigDecimal waste;

    @Value("${delivery.economics.payment-cost-rate:0}")
    private BigDecimal paymentRate;

    @Value("${delivery.economics.journey-cost:0}")
    private BigDecimal journey;

    @Value("${delivery.economics.remedy-cost:0}")
    private BigDecimal remedy;

    @Value("${delivery.economics.fee-floor:0}")
    private BigDecimal feeFloor;

    @Value("${delivery.economics.fee-cap:0}")
    private BigDecimal feeCap;

    @Value("${delivery.economics.minimum-contribution:0}")
    private BigDecimal minimumContribution;

    public DeliveryEconomicsService(EnhancementProperties flags, java.time.Clock inventoryClock) {
        this.flags = flags;
        this.clock = inventoryClock;
    }

    /** Immutable assessment data contract. */
    public record Assessment(
            String version,
            BigDecimal foodCost,
            BigDecimal packagingCost,
            BigDecimal labourCost,
            BigDecimal wasteCost,
            BigDecimal paymentCost,
            BigDecimal journeyCost,
            BigDecimal remedyCost,
            BigDecimal fee,
            BigDecimal contribution,
            boolean viable,
            String alternative) {}

    /**
     * Assesses the operation.
     *
     * @param price the price
     * @return the assess result
     */
    public Assessment assess(OrderCalculationResult price) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryEconomicsService.class, "assess(OrderCalculationResult)");
        try {
            if (!flags.isDeliveryEconomics())
                return new Assessment(
                        "legacy",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        true,
                        null);
            if (version == null || !version.matches("[A-Za-z0-9._-]{1,60}"))
                throw new IllegalStateException(
                        "Delivery cost version is missing; choose pickup instead.");
            try {
                if (java.time.LocalDate.parse(costValidUntil)
                        .isBefore(java.time.LocalDate.now(clock)))
                    throw new IllegalStateException(
                            "Delivery cost inputs are stale; choose pickup instead.");
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalStateException(
                        "Delivery cost validity date is missing; choose pickup instead.");
            }
            for (BigDecimal input :
                    new BigDecimal[] {
                        foodRate,
                        packaging,
                        labour,
                        waste,
                        paymentRate,
                        journey,
                        remedy,
                        feeFloor,
                        feeCap,
                        minimumContribution
                    }) {
                if (input == null || input.signum() < 0)
                    throw new IllegalStateException("Delivery costs are not configured.");
            }
            if (foodRate.compareTo(BigDecimal.ONE) > 0
                    || paymentRate.compareTo(BigDecimal.ONE) > 0
                    || feeFloor.compareTo(feeCap) > 0)
                throw new IllegalStateException("Delivery cost policy is invalid.");
            BigDecimal food = money(price.subtotal().multiply(foodRate));
            BigDecimal processing = money(price.totalAmount().multiply(paymentRate));
            BigDecimal costs =
                    food.add(packaging)
                            .add(labour)
                            .add(waste)
                            .add(processing)
                            .add(journey)
                            .add(remedy);
            BigDecimal needed = costs.add(minimumContribution).subtract(price.subtotal());
            BigDecimal fee = money(needed.max(feeFloor));
            boolean viable = fee.compareTo(feeCap) <= 0;
            if (!viable) fee = BigDecimal.ZERO.setScale(2);
            BigDecimal contribution = money(price.subtotal().add(fee).subtract(costs));
            return new Assessment(
                    version,
                    food,
                    money(packaging),
                    money(labour),
                    money(waste),
                    processing,
                    money(journey),
                    money(remedy),
                    fee,
                    contribution,
                    viable,
                    viable
                            ? null
                            : "This trip is not viable at a fair delivery fee. Choose pickup or"
                                    + " another available window.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryEconomicsService.class,
                    "assess(OrderCalculationResult)");
        }
    }

    /**
     * Money the operation.
     *
     * @param value the value
     * @return the money result
     */
    private static BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryEconomicsService.class, "money(BigDecimal)");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryEconomicsService.class, "money(BigDecimal)");
        }
    }
}
