package com.gokulsweets.restaurant.inventory.dto;

public record InventoryCatalogueSummaryResponse(
        long totalProducts,
        long onlineEnabled,
        long ready,
        long needsAttention,
        long notConfigured,
        long delayed,
        long unavailable
) {
}
