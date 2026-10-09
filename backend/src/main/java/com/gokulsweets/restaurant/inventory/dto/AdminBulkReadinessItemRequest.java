package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable admin bulk readiness item request data contract.
 *
 * @param branchProductId the branch product id
 * @param status the status
 * @param readyQuantity the ready quantity
 * @param expectedReadyAt the expected ready at
 * @param note the note
 */
public record AdminBulkReadinessItemRequest(
        @NotNull(message = "Branch product ID is required.") Long branchProductId,
        @NotNull(message = "Readiness status is required.") InventoryAllocationStatus status,
        @NotNull(message = "Ready quantity is required.")
                @DecimalMin(value = "0.000", message = "Ready quantity cannot be negative.")
                BigDecimal readyQuantity,
        LocalDateTime expectedReadyAt,
        String note) {}
