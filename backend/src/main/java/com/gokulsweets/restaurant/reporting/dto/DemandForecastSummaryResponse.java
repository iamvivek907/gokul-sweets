package com.gokulsweets.restaurant.reporting.dto;

/**
 * Immutable demand forecast summary response data contract.
 *
 * @param productsForecasted the products forecasted
 * @param recommendedUnits the recommended units
 * @param lowerUnits the lower units
 * @param upperUnits the upper units
 * @param highConfidenceProducts the high confidence products
 * @param mediumConfidenceProducts the medium confidence products
 * @param lowConfidenceProducts the low confidence products
 */
public record DemandForecastSummaryResponse(
        int productsForecasted,
        long recommendedUnits,
        long lowerUnits,
        long upperUnits,
        long highConfidenceProducts,
        long mediumConfidenceProducts,
        long lowConfidenceProducts) {}
