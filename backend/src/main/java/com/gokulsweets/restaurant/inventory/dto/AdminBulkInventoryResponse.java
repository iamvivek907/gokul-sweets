package com.gokulsweets.restaurant.inventory.dto;

import java.util.List;

/**
 * Immutable admin bulk inventory response data contract.
 *
 * @param <T> the generic t type
 * @param updatedCount the updated count
 * @param results the results
 */
public record AdminBulkInventoryResponse<T>(int updatedCount, List<T> results) {}
