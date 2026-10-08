package com.gokulsweets.restaurant.reporting.dto;

/**
 * Immutable basket analysis summary response data contract.
 *
 * @param completedOrders the completed orders
 * @param productsInOrders the products in orders
 * @param pairRelationships the pair relationships
 * @param strongPairs the strong pairs
 * @param moderatePairs the moderate pairs
 */
public record BasketAnalysisSummaryResponse(
        long completedOrders,
        long productsInOrders,
        long pairRelationships,
        long strongPairs,
        long moderatePairs) {}
