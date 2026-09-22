package com.gokulsweets.restaurant.reporting.dto;

public record DemandForecastSummaryResponse(

        int productsForecasted,

        long recommendedUnits,

        long lowerUnits,

        long upperUnits,

        long highConfidenceProducts,

        long mediumConfidenceProducts,

        long lowConfidenceProducts
) {
}
