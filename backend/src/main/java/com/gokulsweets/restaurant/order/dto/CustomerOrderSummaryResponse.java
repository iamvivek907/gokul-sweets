package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CustomerOrderSummaryResponse(
        String orderNumber,
        OrderStatus orderStatus,
        String branchName,
        LocalDate pickupDate,
        LocalTime pickupStartTime,
        LocalTime pickupEndTime,
        PickupType pickupType,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime estimatedReadyAt,
        LocalDateTime delayReportedAt
) {
}
