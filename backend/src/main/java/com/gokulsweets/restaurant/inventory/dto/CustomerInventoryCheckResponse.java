package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable customer inventory check response data contract.
 *
 * @param enforcementEnabled the enforcement enabled
 * @param requestedDate the requested date
 * @param orderable the orderable
 * @param suggestedDate the suggested date
 * @param callBranchRecommended the call branch recommended
 * @param items the items
 */
public record CustomerInventoryCheckResponse(
        boolean enforcementEnabled,
        LocalDate requestedDate,
        boolean orderable,
        LocalDate suggestedDate,
        boolean callBranchRecommended,
        List<Item> items) {

    /**
     * Immutable item data contract.
     *
     * @param productId the product id
     * @param productName the product name
     * @param inventoryUnit the inventory unit
     * @param requestedQuantity the requested quantity
     * @param availableQuantity the available quantity
     * @param orderable the orderable
     * @param unavailableReason the unavailable reason
     */
    public record Item(
            Long productId,
            String productName,
            InventoryUnit inventoryUnit,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity,
            boolean orderable,
            String unavailableReason) {}
}
