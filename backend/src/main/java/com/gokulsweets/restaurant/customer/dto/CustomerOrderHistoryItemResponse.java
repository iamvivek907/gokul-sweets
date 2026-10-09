package com.gokulsweets.restaurant.customer.dto;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable customer order history item response data contract.
 *
 * @param orderId the order id
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param branchId the branch id
 * @param branchName the branch name
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param orderStatus the order status
 * @param subtotal the subtotal
 * @param taxAmount the tax amount
 * @param rebateDiscountAmount the rebate discount amount
 * @param totalAmount the total amount
 * @param createdAt the created at
 */
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
