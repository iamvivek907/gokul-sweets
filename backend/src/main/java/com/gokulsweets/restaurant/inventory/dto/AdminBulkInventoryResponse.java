package com.gokulsweets.restaurant.inventory.dto;

import java.util.List;

public record AdminBulkInventoryResponse<T>(
        int updatedCount,
        List<T> results
) {
}
