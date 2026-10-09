package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable executive dashboard highlight response data contract.
 *
 * @param strongestDay the strongest day
 * @param strongestDayRevenue the strongest day revenue
 * @param strongestBranchId the strongest branch id
 * @param strongestBranchName the strongest branch name
 * @param strongestBranchRevenue the strongest branch revenue
 * @param peakPickupHour the peak pickup hour
 * @param peakPickupOrders the peak pickup orders
 * @param topProductId the top product id
 * @param topProductName the top product name
 * @param topProductQuantity the top product quantity
 * @param topProductRevenue the top product revenue
 */
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
