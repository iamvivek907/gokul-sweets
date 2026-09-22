package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        String note
) {

    public static InventoryAllocationResponse from(
            InventoryDailyAllocation allocation,
            InventoryAvailability availability
    ) {
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
                allocation.getNote()
        );
    }
}

