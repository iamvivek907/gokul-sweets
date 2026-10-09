package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable product intelligence response data contract.
 *
 * @param fromDate the from date
 * @param toDate the to date
 * @param comparisonFromDate the comparison from date
 * @param comparisonToDate the comparison to date
 * @param branchId the branch id
 * @param summary the summary
 * @param products the products
 */
public record ProductIntelligenceResponse(
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate comparisonFromDate,
        LocalDate comparisonToDate,
        Long branchId,
        ProductIntelligenceSummaryResponse summary,
        List<ProductIntelligenceItemResponse> products) {}
