package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record ProductIntelligenceResponse(

        LocalDate fromDate,

        LocalDate toDate,

        LocalDate comparisonFromDate,

        LocalDate comparisonToDate,

        Long branchId,

        ProductIntelligenceSummaryResponse summary,

        List<ProductIntelligenceItemResponse> products
) {
}
