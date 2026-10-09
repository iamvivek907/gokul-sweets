package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable inventory allocation response data contract.
 *
 * @param id the id
 * @param branchProductId the branch product id
 * @param productId the product id
 * @param productName the product name
 * @param serviceDate the service date
 * @param inventoryUnit the inventory unit
 * @param status the status
 * @param approvedQuantity the approved quantity
 * @param readyQuantity the ready quantity
 * @param safetyBufferQuantity the safety buffer quantity
 * @param heldQuantity the held quantity
 * @param committedQuantity the committed quantity
 * @param fulfilledQuantity the fulfilled quantity
 * @param wastedQuantity the wasted quantity
 * @param availableQuantity the available quantity
 * @param orderable the orderable
 * @param unavailableReason the unavailable reason
 * @param forecastQuantity the forecast quantity
 * @param forecastConfidence the forecast confidence
 * @param expectedReadyAt the expected ready at
 * @param actualReadyAt the actual ready at
 * @param approvedBy the approved by
 * @param approvedAt the approved at
 * @param note the note
 */
public record InventoryAllocationResponse(
        Long id,
        Long branchProductId,
        Long productId,
        String productName,
        LocalDate serviceDate,
        String inventoryUnit,
        String status,
        BigDecimal approvedQuantity,
        BigDecimal readyQuantity,
        BigDecimal safetyBufferQuantity,
        BigDecimal heldQuantity,
        BigDecimal committedQuantity,
        BigDecimal fulfilledQuantity,
        BigDecimal wastedQuantity,
        BigDecimal availableQuantity,
        boolean orderable,
        String unavailableReason,
        BigDecimal forecastQuantity,
        String forecastConfidence,
        LocalDateTime expectedReadyAt,
        LocalDateTime actualReadyAt,
        String approvedBy,
        LocalDateTime approvedAt,
        String note) {

    /**
     * Maps the supplied data into a {@code InventoryAllocationResponse} representation.
     *
     * @param allocation the allocation supplied to this method
     * @param availability the availability supplied to this method
     * @return the {@code InventoryAllocationResponse} result
     */
    public static InventoryAllocationResponse from(
            InventoryDailyAllocation allocation, InventoryAvailability availability) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryAllocationResponse.class,
                        "from(InventoryDailyAllocation,InventoryAvailability)");
        try {
            return new InventoryAllocationResponse(
                    allocation.getId(),
                    allocation.getBranchProduct().getId(),
                    allocation.getBranchProduct().getProduct().getId(),
                    allocation.getBranchProduct().getProduct().getName(),
                    allocation.getServiceDate(),
                    allocation.getInventoryUnit().name(),
                    allocation.getStatus().name(),
                    allocation.getApprovedQuantity(),
                    allocation.getReadyQuantity(),
                    allocation.getSafetyBufferQuantity(),
                    allocation.getHeldQuantity(),
                    allocation.getCommittedQuantity(),
                    allocation.getFulfilledQuantity(),
                    allocation.getWastedQuantity(),
                    availability.availableQuantity(),
                    availability.orderable(),
                    availability.unavailableReason(),
                    allocation.getForecastQuantity(),
                    allocation.getForecastConfidence(),
                    allocation.getExpectedReadyAt(),
                    allocation.getActualReadyAt(),
                    allocation.getApprovedBy(),
                    allocation.getApprovedAt(),
                    allocation.getNote());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryAllocationResponse.class,
                    "from(InventoryDailyAllocation,InventoryAvailability)");
        }
    }
}
