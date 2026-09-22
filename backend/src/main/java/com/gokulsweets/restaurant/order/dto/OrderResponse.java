package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record OrderResponse(

        Long id,

        String orderNumber,

        Long branchId,

        Long pickupSlotId,

        LocalDate pickupDate,

        LocalTime pickupStartTime,

        LocalTime pickupEndTime,

        String customerName,

        String customerPhone,

        PickupType pickupType,

        BigDecimal priorityCharge,

        BigDecimal subtotal,

        BigDecimal taxAmount,

        BigDecimal totalAmount,

        OrderStatus orderStatus,

        boolean adminOverride,

        String overrideReason,

        List<OrderItemResponse> items,

        LocalDateTime reservationExpiresAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}