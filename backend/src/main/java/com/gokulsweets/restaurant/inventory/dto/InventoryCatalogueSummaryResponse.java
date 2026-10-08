package com.gokulsweets.restaurant.inventory.dto;

/** Immutable inventory catalogue summary response data contract. */
public record InventoryCatalogueSummaryResponse(
        long totalProducts,
        long onlineEnabled,
        long ready,
        long needsAttention,
        long notConfigured,
        long delayed,
        long unavailable) {}
