package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;

import java.time.LocalDateTime;

/**
 * Immutable preparation eligibility data contract.
 *
 * @param status the status
 * @param pickupAt the pickup at
 * @param eligibleAt the eligible at
 * @param minutesUntilPickup the minutes until pickup
 */
public record PreparationEligibility(
        PreparationEligibilityStatus status,
        LocalDateTime pickupAt,
        LocalDateTime eligibleAt,
        long minutesUntilPickup) {

    /**
     * Reports whether start preparation.
     *
     * @return the can start preparation result
     */
    public boolean canStartPreparation() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PreparationEligibility.class, "canStartPreparation()");
        try {
            return status == PreparationEligibilityStatus.ELIGIBLE
                    || status == PreparationEligibilityStatus.OVERDUE;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PreparationEligibility.class,
                    "canStartPreparation()");
        }
    }

    /**
     * Reports whether scheduled.
     *
     * @return the is scheduled result
     */
    public boolean isScheduled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PreparationEligibility.class, "isScheduled()");
        try {
            return status == PreparationEligibilityStatus.SCHEDULED;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PreparationEligibility.class, "isScheduled()");
        }
    }

    /**
     * Reports whether overdue.
     *
     * @return the is overdue result
     */
    public boolean isOverdue() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PreparationEligibility.class, "isOverdue()");
        try {
            return status == PreparationEligibilityStatus.OVERDUE;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PreparationEligibility.class, "isOverdue()");
        }
    }
}
