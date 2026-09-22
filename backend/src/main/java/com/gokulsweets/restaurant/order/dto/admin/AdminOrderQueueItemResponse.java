package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AdminOrderQueueItemResponse(

        String orderNumber,

        Long branchId,

        String branchName,

        String customerName,

        String maskedCustomerPhone,

        LocalDate pickupDate,

        LocalTime pickupStartTime,

        LocalTime pickupEndTime,

        PickupType pickupType,

        BigDecimal totalAmount,

        OrderStatus orderStatus,

        PaymentStatus paymentStatus,

        LocalDateTime createdAt,

        PreparationEligibilityStatus preparationStatus,

        LocalDateTime preparationEligibleAt,

        LocalDateTime pickupAt,

        long minutesUntilPickup

) {
}