package com.gokulsweets.restaurant.inventory.model;

import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable inventory availability data contract.
 *
 * @param branchProductId the branch product id
 * @param serviceDate the service date
 * @param inventoryUnit the inventory unit
 * @param status the status
 * @param availableQuantity the available quantity
 * @param orderable the orderable
 * @param expectedReadyAt the expected ready at
 * @param unavailableReason the unavailable reason
 */
public record InventoryAvailability(
        Long branchProductId,
        LocalDate serviceDate,
        InventoryUnit inventoryUnit,
        InventoryAllocationStatus status,
        BigDecimal availableQuantity,
        boolean orderable,
        LocalDateTime expectedReadyAt,
        String unavailableReason) {}
