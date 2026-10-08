package com.gokulsweets.restaurant.inventory.production.dto;

/** Immutable production plan summary response data contract. */
public record ProductionPlanSummaryResponse(
        long totalItems,
        long criticalItems,
        long needsProductionItems,
        long forecastTopUpItems,
        long readyItems,
        long delayedItems,
        long discrepancyItems) {}
