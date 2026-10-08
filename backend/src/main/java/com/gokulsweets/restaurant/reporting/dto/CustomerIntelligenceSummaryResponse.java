package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable customer intelligence summary response data contract. */
public record CustomerIntelligenceSummaryResponse(
        long customersWithPurchases,
        long repeatCustomers,
        BigDecimal repeatCustomerPercent,
        long verifiedCustomers,
        long activeCustomers,
        long atRiskCustomers,
        long lapsedCustomers,
        long reactivatedCustomers,
        long highValueCustomers,
        long vipCustomers,
        BigDecimal averageLifetimeSpend) {}
