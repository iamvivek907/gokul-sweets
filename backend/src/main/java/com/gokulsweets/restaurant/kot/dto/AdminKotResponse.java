package com.gokulsweets.restaurant.kot.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AdminKotResponse(

        Long id,

        String kotNumber,

        String orderNumber,

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

        List<AdminKotItemResponse> items

) {
}