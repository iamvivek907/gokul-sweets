package com.gokulsweets.restaurant.pickup.dto;

import com.gokulsweets.restaurant.pickup.PickupSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PickupSlotResponse(
        Long id,
        Long branchId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        Integer bookedCount,
        Integer remainingCapacity,
        boolean active,
        boolean priorityEnabled,
        Integer priorityCapacity,
        Integer priorityBookedCount,
        Integer priorityRemainingCapacity,
        BigDecimal priorityCharge
) {

    public static PickupSlotResponse from(PickupSlot slot) {

        int remainingCapacity =
                slot.getCapacity() - slot.getBookedCount();

        int priorityRemainingCapacity =
                slot.getPriorityCapacity()
                        - slot.getPriorityBookedCount();

        return new PickupSlotResponse(
                slot.getId(),
                slot.getBranch().getId(),
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getCapacity(),
                slot.getBookedCount(),
                remainingCapacity,
                slot.isActive(),
                slot.isPriorityEnabled(),
                slot.getPriorityCapacity(),
                slot.getPriorityBookedCount(),
                priorityRemainingCapacity,
                slot.getPriorityCharge()
        );
    }
}