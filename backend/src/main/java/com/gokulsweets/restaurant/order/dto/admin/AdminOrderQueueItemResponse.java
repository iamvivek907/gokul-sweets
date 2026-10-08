package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable admin order queue item response data contract.
 *
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param branchId the branch id
 * @param branchName the branch name
 * @param customerName the customer name
 * @param maskedCustomerPhone the masked customer phone
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param totalAmount the total amount
 * @param orderStatus the order status
 * @param paymentStatus the payment status
 * @param createdAt the created at
 * @param preparationStatus the preparation status
 * @param preparationEligibleAt the preparation eligible at
 * @param pickupAt the pickup at
 * @param minutesUntilPickup the minutes until pickup
 * @param fulfillmentType the fulfillment type
 * @param deliveryDate the delivery date
 * @param deliveryStartTime the delivery start time
 * @param deliveryEndTime the delivery end time
 */
public record AdminOrderQueueItemResponse(
        String orderNumber,
        Long customerOrderNumber,
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
        long minutesUntilPickup,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        LocalTime deliveryStartTime,
        LocalTime deliveryEndTime) {}
