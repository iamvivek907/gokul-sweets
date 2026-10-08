package com.gokulsweets.restaurant.pickup.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable pickup slot response data contract.
 *
 * @param id the id
 * @param branchId the branch id
 * @param slotDate the slot date
 * @param startTime the start time
 * @param endTime the end time
 * @param capacity the capacity
 * @param bookedCount the booked count
 * @param remainingCapacity the remaining capacity
 * @param active the active
 * @param priorityEnabled the priority enabled
 * @param priorityCapacity the priority capacity
 * @param priorityBookedCount the priority booked count
 * @param priorityRemainingCapacity the priority remaining capacity
 * @param priorityCharge the priority charge
 */
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
        BigDecimal priorityCharge) {

    /**
     * Froms the operation.
     *
     * @param slot the slot
     * @return the from result
     */
    public static PickupSlotResponse from(PickupSlot slot) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupSlotResponse.class, "from(PickupSlot)");
        try {
            int remainingCapacity = slot.getCapacity() - slot.getBookedCount();
            int priorityRemainingCapacity =
                    slot.getPriorityCapacity() - slot.getPriorityBookedCount();
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
                    slot.getPriorityCharge());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupSlotResponse.class, "from(PickupSlot)");
        }
    }
}
