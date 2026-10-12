package com.gokulsweets.restaurant.inventory.production.dto;

/**
 * Immutable production plan summary response data contract.
 *
 * @param totalItems the total items
 * @param criticalItems the critical items
 * @param needsProductionItems the needs production items
 * @param forecastTopUpItems the forecast top up items
 * @param readyItems the ready items
 * @param delayedItems the delayed items
 * @param discrepancyItems the discrepancy items
 */
public record ProductionPlanSummaryResponse(
        long totalItems,
        long criticalItems,
        long needsProductionItems,
        long forecastTopUpItems,
        long readyItems,
        long delayedItems,
        long discrepancyItems) {}
