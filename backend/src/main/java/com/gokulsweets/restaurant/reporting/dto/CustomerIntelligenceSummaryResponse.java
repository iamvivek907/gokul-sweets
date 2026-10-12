package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable customer intelligence summary response data contract.
 *
 * @param customersWithPurchases the customers with purchases
 * @param repeatCustomers the repeat customers
 * @param repeatCustomerPercent the repeat customer percent
 * @param verifiedCustomers the verified customers
 * @param activeCustomers the active customers
 * @param atRiskCustomers the at risk customers
 * @param lapsedCustomers the lapsed customers
 * @param reactivatedCustomers the reactivated customers
 * @param highValueCustomers the high value customers
 * @param vipCustomers the vip customers
 * @param averageLifetimeSpend the average lifetime spend
 */
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
