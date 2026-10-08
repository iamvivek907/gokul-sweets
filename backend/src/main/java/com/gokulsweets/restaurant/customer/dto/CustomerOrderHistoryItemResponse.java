package com.gokulsweets.restaurant.customer.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Immutable customer order history item response data contract. */
public record CustomerOrderHistoryItemResponse(
        Long orderId,
        String orderNumber,
        Long customerOrderNumber,
        Long branchId,
        String branchName,
        LocalDate pickupDate,
        LocalTime pickupStartTime,
        LocalTime pickupEndTime,
        PickupType pickupType,
        OrderStatus orderStatus,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal rebateDiscountAmount,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {}
