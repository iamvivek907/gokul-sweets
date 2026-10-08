package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable sales branch mix response data contract. */
public record SalesBranchMixResponse(
        Long branchId,
        String branchCode,
        String branchName,
        long completedOrders,
        long unitsSold,
        BigDecimal revenue,
        BigDecimal revenueSharePercent) {}
