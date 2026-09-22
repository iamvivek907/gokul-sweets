package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;

import java.time.LocalDateTime;

public record PreparationEligibility(

        PreparationEligibilityStatus status,

        LocalDateTime pickupAt,

        LocalDateTime eligibleAt,

        long minutesUntilPickup

) {

    public boolean canStartPreparation() {

        return status
                ==
                PreparationEligibilityStatus.ELIGIBLE
                ||
                status
                        ==
                        PreparationEligibilityStatus.OVERDUE;
    }


    public boolean isScheduled() {

        return status
                ==
                PreparationEligibilityStatus.SCHEDULED;
    }


    public boolean isOverdue() {

        return status
                ==
                PreparationEligibilityStatus.OVERDUE;
    }
}