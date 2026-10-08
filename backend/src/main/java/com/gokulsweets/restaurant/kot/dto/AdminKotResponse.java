package com.gokulsweets.restaurant.kot.dto;

import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.PickupType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable admin kot response data contract.
 *
 * @param id the id
 * @param kotNumber the kot number
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param branchId the branch id
 * @param branchName the branch name
 * @param branchAddress the branch address
 * @param pickupDate the pickup date
 * @param pickupStartTime the pickup start time
 * @param pickupEndTime the pickup end time
 * @param pickupType the pickup type
 * @param startedByStaffId the started by staff id
 * @param startedByStaffName the started by staff name
 * @param createdAt the created at
 * @param firstPrintedAt the first printed at
 * @param lastPrintedAt the last printed at
 * @param printCount the print count
 * @param items the items
 * @param fulfillmentType the fulfillment type
 * @param deliveryDate the delivery date
 * @param deliveryStartTime the delivery start time
 * @param deliveryEndTime the delivery end time
 */
public record AdminKotResponse(
        Long id,
        String kotNumber,
        String orderNumber,
        Long customerOrderNumber,
        Long branchId,
        String branchName,
        String branchAddress,
        LocalDate pickupDate,
        LocalTime pickupStartTime,
        LocalTime pickupEndTime,
        PickupType pickupType,
        Long startedByStaffId,
        String startedByStaffName,
        LocalDateTime createdAt,
        LocalDateTime firstPrintedAt,
        LocalDateTime lastPrintedAt,
        Integer printCount,
        List<AdminKotItemResponse> items,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        LocalTime deliveryStartTime,
        LocalTime deliveryEndTime) {}
