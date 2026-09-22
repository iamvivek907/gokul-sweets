package com.gokulsweets.restaurant.inventory.dto;

import java.time.LocalDate;
import java.util.List;

public record InventoryCataloguePageResponse(
        Long branchId,
        LocalDate serviceDate,
        List<InventoryCatalogueItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        InventoryCatalogueSummaryResponse summary,
        List<InventoryCategoryOptionResponse> categories
) {
}
