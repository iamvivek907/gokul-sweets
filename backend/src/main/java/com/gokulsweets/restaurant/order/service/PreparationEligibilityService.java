package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PreparationEligibilityService {

    private final PreparationWindowProperties
            preparationWindowProperties;
    private final JdbcTemplate jdbc;


    /*
     * =========================================================
     * EVALUATE USING CURRENT SERVER TIME
     * =========================================================
     */

    public PreparationEligibility evaluate(
            Order order
    ) {

        return evaluate(
                order,
                ApplicationClock.legacyTimestampNow()
        );
    }


    /*
     * =========================================================
     * EVALUATE USING EXPLICIT TIME
     * =========================================================
     *
     * The explicit-time overload makes the business rule
     * deterministic and easy to unit test.
     */

    public PreparationEligibility evaluate(
            Order order,
            LocalDateTime now
    ) {

        if (order == null) {

            throw new IllegalArgumentException(
                    "Order is required."
            );
        }


        if (now == null) {

            throw new IllegalArgumentException(
                    "Current time is required."
            );
        }


        /*
         * Preparation eligibility is only meaningful while
         * an order is waiting in CONFIRMED state.
         */
        if (
                order.getOrderStatus()
                        !=
                        OrderStatus.CONFIRMED
        ) {

            return new PreparationEligibility(
                    PreparationEligibilityStatus.NOT_APPLICABLE,
                    null,
                    null,
                    0
            );
        }


        if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
            if (order.getDeliveryWindowId() == null || order.getBranch() == null)
                throw new IllegalStateException("Delivery window is unavailable for the confirmed order.");
            var starts = jdbc.query("""
                    SELECT w.service_date, w.starts_at FROM delivery_capacity_windows w
                    JOIN delivery_zones z ON z.id = w.zone_id
                    WHERE w.id = ? AND z.branch_id = ?
                    """, (rs, row) -> LocalDateTime.of(rs.getDate(1).toLocalDate(),
                    rs.getTime(2).toLocalTime()), order.getDeliveryWindowId(), order.getBranch().getId());
            if (starts.isEmpty())
                throw new IllegalStateException("Delivery window is unavailable for the confirmed order.");
            int leadMinutes = preparationWindowProperties.getDeliveryLeadMinutes();
            if (leadMinutes < 0) throw new IllegalStateException("Delivery preparation lead minutes cannot be negative.");
            return eligibility(starts.getFirst(), leadMinutes, now);
        }

        PickupSlot pickupSlot =
                order.getPickupSlot();


        if (pickupSlot == null) {

            throw new IllegalStateException(
                    "Pickup slot is unavailable for the confirmed order."
            );
        }


        if (pickupSlot.getSlotDate() == null) {

            throw new IllegalStateException(
                    "Pickup slot date is unavailable."
            );
        }


        if (pickupSlot.getStartTime() == null) {

            throw new IllegalStateException(
                    "Pickup slot start time is unavailable."
            );
        }


        PickupType pickupType =
                order.getPickupType();


        if (pickupType == null) {

            throw new IllegalStateException(
                    "Pickup type is unavailable for the confirmed order."
            );
        }


        LocalDateTime pickupAt =
                LocalDateTime.of(
                        pickupSlot.getSlotDate(),
                        pickupSlot.getStartTime()
                );


        int leadMinutes =
                resolveLeadMinutes(
                        pickupType
                );


        return eligibility(pickupAt, leadMinutes, now);
    }

    private PreparationEligibility eligibility(LocalDateTime pickupAt, int leadMinutes, LocalDateTime now) {
        LocalDateTime eligibleAt =
                pickupAt.minusMinutes(
                        leadMinutes
                );


        long minutesUntilPickup =
                Duration.between(
                                now,
                                pickupAt
                        )
                        .toMinutes();


        /*
         * The pickup start boundary has priority.
         *
         * At exactly pickupAt, a CONFIRMED order is already
         * overdue because preparation should have started
         * earlier.
         */
        if (
                !now.isBefore(
                        pickupAt
                )
        ) {

            return new PreparationEligibility(
                    PreparationEligibilityStatus.OVERDUE,
                    pickupAt,
                    eligibleAt,
                    minutesUntilPickup
            );
        }


        /*
         * The preparation window has not opened yet.
         */
        if (
                now.isBefore(
                        eligibleAt
                )
        ) {

            return new PreparationEligibility(
                    PreparationEligibilityStatus.SCHEDULED,
                    pickupAt,
                    eligibleAt,
                    minutesUntilPickup
            );
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
                minutesUntilPickup
        );
    }


    /*
     * =========================================================
     * CAN START PREPARATION
     * =========================================================
     */

    public boolean canStartPreparation(
            Order order
    ) {

        return evaluate(
                order
        )
                .canStartPreparation();
    }


    public boolean canStartPreparation(
            Order order,
            LocalDateTime now
    ) {

        return evaluate(
                order,
                now
        )
                .canStartPreparation();
    }


    /*
     * =========================================================
     * PREPARATION LEAD WINDOW
     * =========================================================
     */

    private int resolveLeadMinutes(
            PickupType pickupType
    ) {

        int leadMinutes =
                switch (pickupType) {

                    case NORMAL ->
                            preparationWindowProperties
                                    .getNormalLeadMinutes();

                    case PRIORITY ->
                            preparationWindowProperties
                                    .getPriorityLeadMinutes();

                    case ADMIN_OVERRIDE ->
                            preparationWindowProperties
                                    .getAdminOverrideLeadMinutes();
                };


        if (leadMinutes < 0) {

            throw new IllegalStateException(
                    "Preparation lead minutes cannot be negative."
            );
        }


        return leadMinutes;
    }
}
