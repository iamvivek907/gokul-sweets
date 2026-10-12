package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable executive dashboard branch response data contract.
 *
 * @param branchId the branch id
 * @param branchCode the branch code
 * @param branchName the branch name
 * @param revenue the revenue
 * @param orders the orders
 * @param unitsSold the units sold
 */
public record ExecutiveDashboardBranchResponse(
        Long branchId,
        String branchCode,
        String branchName,
        BigDecimal revenue,
        long orders,
        long unitsSold) {}
