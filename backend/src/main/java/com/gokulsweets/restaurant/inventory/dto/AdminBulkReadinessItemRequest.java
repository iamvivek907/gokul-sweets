package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminBulkReadinessItemRequest(
        @NotNull(message = "Branch product ID is required.")
        Long branchProductId,

        @NotNull(message = "Readiness status is required.")
        InventoryAllocationStatus status,

        @NotNull(message = "Ready quantity is required.")
        @DecimalMin(value = "0.000", message = "Ready quantity cannot be negative.")
        BigDecimal readyQuantity,

        LocalDateTime expectedReadyAt,

        String note
) {
}
