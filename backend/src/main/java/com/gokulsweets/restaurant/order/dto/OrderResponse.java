package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable order response data contract.
 *
 * @param id the id
 * @param orderNumber the order number
 * @param branchId the branch id
 * @param pickupSlotId the pickup slot id
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param customerName the customer name
 * @param customerPhone the customer phone
 * @param pickupType the pickup type
 * @param priorityCharge the priority charge
 * @param convenienceFee the convenience fee
 * @param convenienceFeeTax the convenience fee tax
 * @param paymentFee the payment fee
 * @param paymentFeeTax the payment fee tax
 * @param paymentFeeRate the payment fee rate
 * @param subtotal the subtotal
 * @param taxAmount the tax amount
 * @param totalAmount the total amount
 * @param orderStatus the order status
 * @param adminOverride the admin override
 * @param overrideReason the override reason
 * @param items the items
 * @param reservationExpiresAt the reservation expires at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
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
        BigDecimal convenienceFee,
        BigDecimal convenienceFeeTax,
        BigDecimal paymentFee,
        BigDecimal paymentFeeTax,
        BigDecimal paymentFeeRate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        OrderStatus orderStatus,
        boolean adminOverride,
        String overrideReason,
        List<OrderItemResponse> items,
        LocalDateTime reservationExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
