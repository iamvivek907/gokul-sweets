package com.gokulsweets.restaurant.inventory.production.dto;

public record ProductionPlanSummaryResponse(
        long totalItems,
        long criticalItems,
        long needsProductionItems,
        long forecastTopUpItems,
        long readyItems,
        long delayedItems,
        long discrepancyItems
) {
}
