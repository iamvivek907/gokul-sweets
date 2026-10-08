package com.gokulsweets.restaurant.inventory.dto;

import java.util.List;

/** Immutable admin bulk inventory response data contract. */
public record AdminBulkInventoryResponse<T>(int updatedCount, List<T> results) {}
