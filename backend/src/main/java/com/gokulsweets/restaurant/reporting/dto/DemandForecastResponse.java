package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record DemandForecastResponse(

        LocalDate targetDate,

        Long branchId,

        String branchName,

        int historyWeeks,

        DemandForecastSummaryResponse summary,

        List<DemandForecastItemResponse> products
) {
}
