package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable sales branch mix response data contract.
 *
 * @param branchId the branch id
 * @param branchCode the branch code
 * @param branchName the branch name
 * @param completedOrders the completed orders
 * @param unitsSold the units sold
 * @param revenue the revenue
 * @param revenueSharePercent the revenue share percent
 */
public record SalesBranchMixResponse(
        Long branchId,
        String branchCode,
        String branchName,
        long completedOrders,
        long unitsSold,
        BigDecimal revenue,
        BigDecimal revenueSharePercent) {}
