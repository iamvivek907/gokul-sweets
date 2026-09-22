package com.gokulsweets.restaurant.reporting.dto;

public record BasketAnalysisSummaryResponse(

        long completedOrders,

        long productsInOrders,

        long pairRelationships,

        long strongPairs,

        long moderatePairs
) {
}
