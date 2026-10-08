package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable executive dashboard highlight response data contract. */
public record ExecutiveDashboardHighlightResponse(
        LocalDate strongestDay,
        BigDecimal strongestDayRevenue,
        Long strongestBranchId,
        String strongestBranchName,
        BigDecimal strongestBranchRevenue,
        Integer peakPickupHour,
        long peakPickupOrders,
        Long topProductId,
        String topProductName,
        long topProductQuantity,
        BigDecimal topProductRevenue) {}
