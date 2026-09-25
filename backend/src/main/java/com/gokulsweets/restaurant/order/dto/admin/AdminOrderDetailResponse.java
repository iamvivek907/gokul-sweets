package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AdminOrderDetailResponse(

        String orderNumber,

        Long branchId,

        String branchName,

        String branchAddress,

        String customerName,

        String customerPhone,

        LocalDate pickupDate,

        LocalTime pickupStartTime,

        LocalTime pickupEndTime,

        PickupType pickupType,

        OrderStatus orderStatus,

        PaymentStatus paymentStatus,

        BigDecimal subtotal,

        BigDecimal taxAmount,

        BigDecimal priorityCharge,

        BigDecimal totalAmount,

        boolean adminOverride,

        String overrideReason,

        List<OrderItemResponse> items,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,
        LocalDateTime estimatedReadyAt,
        String delayReason,
        LocalDateTime delayReportedAt
) {
}
