package com.gokulsweets.restaurant.inventory.model;

import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryAvailability(
        Long branchProductId,
        LocalDate serviceDate,
        InventoryUnit inventoryUnit,
        InventoryAllocationStatus status,
        BigDecimal availableQuantity,
        boolean orderable,
        LocalDateTime expectedReadyAt,
        String unavailableReason
) {
}

