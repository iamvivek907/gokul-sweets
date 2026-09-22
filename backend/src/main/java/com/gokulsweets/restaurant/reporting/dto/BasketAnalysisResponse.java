package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record BasketAnalysisResponse(

        LocalDate fromDate,

        LocalDate toDate,

        Long branchId,

        BasketAnalysisSummaryResponse summary,

        List<BasketPairResponse> pairs
) {
}
