package com.gokulsweets.restaurant.order.enums;

public enum PreparationEligibilityStatus {

    /*
     * Confirmed order exists, but its preparation
     * window has not started yet.
     */
    SCHEDULED,


    /*
     * Preparation window has started and the pickup
     * start time has not yet passed.
     */
    ELIGIBLE,


    /*
     * Pickup start time has already been reached or
     * passed while the order remains CONFIRMED.
     */
    OVERDUE,


    /*
     * Preparation eligibility is only relevant to
     * CONFIRMED orders.
     */
    NOT_APPLICABLE
}