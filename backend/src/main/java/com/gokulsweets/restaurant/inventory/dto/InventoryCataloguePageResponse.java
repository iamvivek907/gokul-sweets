package com.gokulsweets.restaurant.inventory.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable inventory catalogue page response data contract.
 *
 * @param branchId the branch id
 * @param serviceDate the service date
 * @param content the content
 * @param page the page
 * @param size the size
 * @param totalElements the total elements
 * @param totalPages the total pages
 * @param summary the summary
 * @param categories the categories
 */
public record InventoryCataloguePageResponse(
        Long branchId,
        LocalDate serviceDate,
        List<InventoryCatalogueItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        InventoryCatalogueSummaryResponse summary,
        List<InventoryCategoryOptionResponse> categories) {}
