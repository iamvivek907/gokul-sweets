package com.gokulsweets.restaurant.inventory.dto;

/**
 * Immutable inventory catalogue summary response data contract.
 *
 * @param totalProducts the total products
 * @param onlineEnabled the online enabled
 * @param ready the ready
 * @param needsAttention the needs attention
 * @param notConfigured the not configured
 * @param delayed the delayed
 * @param unavailable the unavailable
 */
public record InventoryCatalogueSummaryResponse(
        long totalProducts,
        long onlineEnabled,
        long ready,
        long needsAttention,
        long notConfigured,
        long delayed,
        long unavailable) {}
