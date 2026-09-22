package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record SalesBranchMixResponse(

        Long branchId,

        String branchCode,

        String branchName,

        long completedOrders,

        long unitsSold,

        BigDecimal revenue,

        BigDecimal revenueSharePercent
) {
}
