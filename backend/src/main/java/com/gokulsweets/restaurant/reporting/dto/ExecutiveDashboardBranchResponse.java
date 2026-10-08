package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable executive dashboard branch response data contract. */
public record ExecutiveDashboardBranchResponse(
        Long branchId,
        String branchCode,
        String branchName,
        BigDecimal revenue,
        long orders,
        long unitsSold) {}
