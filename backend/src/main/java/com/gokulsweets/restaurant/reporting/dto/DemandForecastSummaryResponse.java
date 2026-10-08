package com.gokulsweets.restaurant.reporting.dto;

/** Immutable demand forecast summary response data contract. */
public record DemandForecastSummaryResponse(
        int productsForecasted,
        long recommendedUnits,
        long lowerUnits,
        long upperUnits,
        long highConfidenceProducts,
        long mediumConfidenceProducts,
        long lowConfidenceProducts) {}
