package com.gokulsweets.restaurant.order.dto;

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
 * Immutable customer order response data contract.
 *
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param orderStatus the order status
 * @param paymentStatus the payment status
 * @param branchName the branch name
 * @param branchAddress the branch address
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param customerName the customer name
 * @param maskedCustomerPhone the masked customer phone
 * @param items the items
 * @param subtotal the subtotal
 * @param taxAmount the tax amount
 * @param priorityCharge the priority charge
 * @param convenienceFee the convenience fee
 * @param convenienceFeeTax the convenience fee tax
 * @param paymentFee the payment fee
 * @param paymentFeeTax the payment fee tax
 * @param paymentFeeRate the payment fee rate
 * @param totalAmount the total amount
 * @param reservationExpiresAt the reservation expires at
 * @param createdAt the created at
 * @param updatedAt the updated at
 * @param branchPhone the branch phone
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
 * @param branchFssaiLicenceNumber the branch fssai licence number
 * @param deliveryFee the delivery fee
 * @param loyaltyDiscount the loyalty discount
 * @param loyaltyCoins the loyalty coins
 * @param loyaltyRewardCode the loyalty reward code
 * @param rebateDiscountAmount the rebate discount amount
 * @param branchId the branch id
 * @param pickupSlotId the pickup slot id
 */
public record CustomerOrderResponse(
        String orderNumber,
        Long customerOrderNumber,
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
        BigDecimal convenienceFee,
        BigDecimal convenienceFeeTax,
        BigDecimal paymentFee,
        BigDecimal paymentFeeTax,
        BigDecimal paymentFeeRate,
        BigDecimal totalAmount,
        LocalDateTime reservationExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String branchPhone,
        LocalDateTime estimatedReadyAt,
        String delayReason,
        LocalDateTime delayReportedAt,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        LocalTime deliveryStartTime,
        LocalTime deliveryEndTime,
        String deliveryAddressLine,
        String deliveryLocality,
        String deliveryPostalCode,
        String branchFssaiLicenceNumber,
        BigDecimal deliveryFee,
        BigDecimal loyaltyDiscount,
        int loyaltyCoins,
        String loyaltyRewardCode,
        BigDecimal rebateDiscountAmount,
        Long branchId,
        Long pickupSlotId) {}
