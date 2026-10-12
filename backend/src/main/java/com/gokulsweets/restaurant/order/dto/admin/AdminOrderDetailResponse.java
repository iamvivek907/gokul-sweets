package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable admin order detail response data contract.
 *
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param branchId the branch id
 * @param branchName the branch name
 * @param branchAddress the branch address
 * @param customerName the customer name
 * @param customerPhone the customer phone
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param orderStatus the order status
 * @param paymentStatus the payment status
 * @param subtotal the subtotal
 * @param taxAmount the tax amount
 * @param priorityCharge the priority charge
 * @param convenienceFee the convenience fee
 * @param convenienceFeeTax the convenience fee tax
 * @param totalAmount the total amount
 * @param adminOverride the admin override
 * @param overrideReason the override reason
 * @param items the items
 * @param createdAt the created at
 * @param updatedAt the updated at
 * @param estimatedReadyAt the estimated ready at
 * @param delayReason the delay reason
 * @param delayReportedAt the delay reported at
 * @param fulfillmentType the fulfillment type
 * @param deliveryDate the delivery date
 * @param deliveryStartTime the delivery start time
 * @param deliveryEndTime the delivery end time
 * @param deliveryAddressLine the delivery address line
 * @param deliveryLocality the delivery locality
 * @param deliveryPostalCode the delivery postal code
 */
public record AdminOrderDetailResponse(
        String orderNumber,
        Long customerOrderNumber,
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
        BigDecimal convenienceFee,
        BigDecimal convenienceFeeTax,
        BigDecimal totalAmount,
        boolean adminOverride,
        String overrideReason,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime estimatedReadyAt,
        String delayReason,
        LocalDateTime delayReportedAt,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        LocalTime deliveryStartTime,
        LocalTime deliveryEndTime,
        String deliveryAddressLine,
        String deliveryLocality,
        String deliveryPostalCode) {}
