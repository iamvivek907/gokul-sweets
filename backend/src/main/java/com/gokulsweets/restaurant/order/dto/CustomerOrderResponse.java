package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record CustomerOrderResponse(

        String orderNumber,

        OrderStatus orderStatus,

        PaymentStatus paymentStatus,

        String branchName,

        String branchAddress,

        LocalDate pickupDate,

        LocalTime pickupStartTime,

        LocalTime pickupEndTime,

        PickupType pickupType,

        String customerName,

        String maskedCustomerPhone,

        List<OrderItemResponse> items,

        BigDecimal subtotal,

        BigDecimal taxAmount,

        BigDecimal priorityCharge,

        BigDecimal totalAmount,

        LocalDateTime reservationExpiresAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        String branchPhone,
        LocalDateTime estimatedReadyAt,
        String delayReason,
        LocalDateTime delayReportedAt
) {
}
