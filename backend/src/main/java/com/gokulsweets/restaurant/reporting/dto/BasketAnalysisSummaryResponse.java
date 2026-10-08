package com.gokulsweets.restaurant.reporting.dto;

/** Immutable basket analysis summary response data contract. */
public record BasketAnalysisSummaryResponse(
        long completedOrders,
        long productsInOrders,
        long pairRelationships,
        long strongPairs,
        long moderatePairs) {}
