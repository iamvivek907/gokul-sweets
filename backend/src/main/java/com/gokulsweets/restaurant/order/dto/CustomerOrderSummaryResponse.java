package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable customer order summary response data contract.
 *
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param orderStatus the order status
 * @param branchId the branch id
 * @param branchName the branch name
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param totalAmount the total amount
 * @param createdAt the created at
 * @param updatedAt the updated at
 * @param estimatedReadyAt the estimated ready at
 * @param delayReportedAt the delay reported at
 * @param fulfillmentType the fulfillment type
 * @param deliveryDate the delivery date
 * @param deliveryStartTime the delivery start time
 * @param deliveryEndTime the delivery end time
 */
public record CustomerOrderSummaryResponse(
        String orderNumber,
        Long customerOrderNumber,
        OrderStatus orderStatus,
        Long branchId,
        String branchName,
        LocalDate pickupDate,
        LocalTime pickupStartTime,
        LocalTime pickupEndTime,
        PickupType pickupType,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime estimatedReadyAt,
        LocalDateTime delayReportedAt,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        LocalTime deliveryStartTime,
        LocalTime deliveryEndTime) {}
