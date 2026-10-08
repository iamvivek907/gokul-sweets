package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/** Coordinates preparation eligibility operations. */
@Service
@RequiredArgsConstructor
public class PreparationEligibilityService {

    private final PreparationWindowProperties preparationWindowProperties;

    private final JdbcTemplate jdbc;

    /*
     * =========================================================
     * EVALUATE USING CURRENT SERVER TIME
     * =========================================================
     */
    /**
     * Evaluates the operation.
     *
     * @param order the order
     * @return the evaluate result
     */
    public PreparationEligibility evaluate(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PreparationEligibilityService.class, "evaluate(Order)");
        try {
            return evaluate(order, ApplicationClock.legacyTimestampNow());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "evaluate(Order)");
        }
    }

    /*
     * =========================================================
     * EVALUATE USING EXPLICIT TIME
     * =========================================================
     *
     * The explicit-time overload makes the business rule
     * deterministic and easy to unit test.
     */
    /**
     * Evaluates the operation.
     *
     * @param order the order
     * @param now the now
     * @return the evaluate result
     */
    public PreparationEligibility evaluate(Order order, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationEligibilityService.class, "evaluate(Order,LocalDateTime)");
        try {
            if (order == null) {
                throw new IllegalArgumentException("Order is required.");
            }
            if (now == null) {
                throw new IllegalArgumentException("Current time is required.");
            }
            /*
             * Preparation eligibility is only meaningful while
             * an order is waiting in CONFIRMED state.
             */
            if (order.getOrderStatus() != OrderStatus.CONFIRMED) {
                return new PreparationEligibility(
                        PreparationEligibilityStatus.NOT_APPLICABLE, null, null, 0);
            }
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
                if (order.getDeliveryWindowId() == null || order.getBranch() == null)
                    throw new IllegalStateException(
                            "Delivery window is unavailable for the confirmed order.");
                var starts =
                        jdbc.query(
                                """
                                SELECT w.service_date, w.starts_at FROM delivery_capacity_windows w
                                JOIN delivery_zones z ON z.id = w.zone_id
                                WHERE w.id = ? AND z.branch_id = ?
                                """,
                                (rs, row) ->
                                        LocalDateTime.of(
                                                rs.getDate(1).toLocalDate(),
                                                rs.getTime(2).toLocalTime()),
                                order.getDeliveryWindowId(),
                                order.getBranch().getId());
                if (starts.isEmpty())
                    throw new IllegalStateException(
                            "Delivery window is unavailable for the confirmed order.");
                int leadMinutes = preparationWindowProperties.getDeliveryLeadMinutes();
                if (leadMinutes < 0)
                    throw new IllegalStateException(
                            "Delivery preparation lead minutes cannot be negative.");
                return eligibility(starts.getFirst(), leadMinutes, now);
            }
            PickupSlot pickupSlot = order.getPickupSlot();
            if (pickupSlot == null) {
                throw new IllegalStateException(
                        "Pickup slot is unavailable for the confirmed order.");
            }
            if (pickupSlot.getSlotDate() == null) {
                throw new IllegalStateException("Pickup slot date is unavailable.");
            }
            if (pickupSlot.getStartTime() == null) {
                throw new IllegalStateException("Pickup slot start time is unavailable.");
            }
            PickupType pickupType = order.getPickupType();
            if (pickupType == null) {
                throw new IllegalStateException(
                        "Pickup type is unavailable for the confirmed order.");
            }
            LocalDateTime pickupAt =
                    LocalDateTime.of(pickupSlot.getSlotDate(), pickupSlot.getStartTime());
            int leadMinutes = resolveLeadMinutes(pickupType);
            // Counter-only orders may be packed early on their service day. Mixed baskets
            // and missing policies keep the existing preparation window.
            if (order.getId() != null
                    && order.getBranch() != null
                    && Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
SELECT COUNT(*)>0 AND BOOL_AND(COALESCE(bp.early_preparation_allowed,FALSE))
FROM order_items i LEFT JOIN branch_products bp
  ON bp.product_id=i.product_id AND bp.branch_id=? WHERE i.order_id=?
""",
                                    Boolean.class,
                                    order.getBranch().getId(),
                                    order.getId()))) {
                leadMinutes = Math.max(leadMinutes, pickupAt.getHour() * 60 + pickupAt.getMinute());
            }
            return eligibility(pickupAt, leadMinutes, now);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "evaluate(Order,LocalDateTime)");
        }
    }

    /**
     * Eligibility the operation.
     *
     * @param pickupAt the pickup at
     * @param leadMinutes the lead minutes
     * @param now the now
     * @return the eligibility result
     */
    private PreparationEligibility eligibility(
            LocalDateTime pickupAt, int leadMinutes, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationEligibilityService.class,
                        "eligibility(LocalDateTime,int,LocalDateTime)");
        try {
            LocalDateTime eligibleAt = pickupAt.minusMinutes(leadMinutes);
            long minutesUntilPickup = Duration.between(now, pickupAt).toMinutes();
            /*
             * The pickup start boundary has priority.
             *
             * At exactly pickupAt, a CONFIRMED order is already
             * overdue because preparation should have started
             * earlier.
             */
            if (!now.isBefore(pickupAt)) {
                return new PreparationEligibility(
                        PreparationEligibilityStatus.OVERDUE,
                        pickupAt,
                        eligibleAt,
                        minutesUntilPickup);
            }
            /*
             * The preparation window has not opened yet.
             */
            if (now.isBefore(eligibleAt)) {
                return new PreparationEligibility(
                        PreparationEligibilityStatus.SCHEDULED,
                        pickupAt,
                        eligibleAt,
                        minutesUntilPickup);
            }
            /*
             * We are between:
             *
             * eligibleAt <= now < pickupAt
             */
            return new PreparationEligibility(
                    PreparationEligibilityStatus.ELIGIBLE,
                    pickupAt,
                    eligibleAt,
                    minutesUntilPickup);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "eligibility(LocalDateTime,int,LocalDateTime)");
        }
    }

    /*
     * =========================================================
     * CAN START PREPARATION
     * =========================================================
     */
    /**
     * Reports whether start preparation.
     *
     * @param order the order
     * @return the can start preparation result
     */
    public boolean canStartPreparation(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationEligibilityService.class, "canStartPreparation(Order)");
        try {
            return evaluate(order).canStartPreparation();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "canStartPreparation(Order)");
        }
    }

    /**
     * Reports whether start preparation.
     *
     * @param order the order
     * @param now the now
     * @return the can start preparation result
     */
    public boolean canStartPreparation(Order order, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationEligibilityService.class,
                        "canStartPreparation(Order,LocalDateTime)");
        try {
            return evaluate(order, now).canStartPreparation();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "canStartPreparation(Order,LocalDateTime)");
        }
    }

    /*
     * =========================================================
     * PREPARATION LEAD WINDOW
     * =========================================================
     */
    /**
     * Resolves lead minutes.
     *
     * @param pickupType the pickup type
     * @return the resolve lead minutes result
     */
    private int resolveLeadMinutes(PickupType pickupType) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PreparationEligibilityService.class, "resolveLeadMinutes(PickupType)");
        try {
            int leadMinutes =
                    switch (pickupType) {
                        case NORMAL -> preparationWindowProperties.getNormalLeadMinutes();
                        case PRIORITY -> preparationWindowProperties.getPriorityLeadMinutes();
                        case ADMIN_OVERRIDE ->
                                preparationWindowProperties.getAdminOverrideLeadMinutes();
                    };
            if (leadMinutes < 0) {
                throw new IllegalStateException("Preparation lead minutes cannot be negative.");
            }
            return leadMinutes;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibilityService.class,
                    "resolveLeadMinutes(PickupType)");
        }
    }
}
