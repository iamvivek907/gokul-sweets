package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

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

        BigDecimal averageLifetimeSpend
) {
}
