package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerInventoryCheckResponse(
        boolean enforcementEnabled,
        LocalDate requestedDate,
        boolean orderable,
        LocalDate suggestedDate,
        boolean callBranchRecommended,
        List<Item> items
) {
    public record Item(
            Long productId,
            String productName,
            InventoryUnit inventoryUnit,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity,
            boolean orderable,
            String unavailableReason
    ) {
    }
}
