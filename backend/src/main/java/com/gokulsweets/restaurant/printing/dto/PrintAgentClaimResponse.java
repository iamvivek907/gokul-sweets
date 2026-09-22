package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.printing.enums.PrinterProtocol;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record PrintAgentClaimResponse(

        Long printJobId,

        String claimToken,

        LocalDateTime leaseExpiresAt,

        Integer copies,

        Printer printer,

        KotPayload kot

) {

    public record Printer(

            Long id,

            String code,

            String name,

            PrinterStation station,

            PrinterProtocol protocol,

            String host,

            Integer port,

            Integer paperWidthMm,

            boolean autoCut
    ) {
    }


    public record KotPayload(

            Long kotId,

            String kotNumber,

            String orderNumber,

            String branchName,

            String branchAddress,

            LocalDate pickupDate,

            LocalTime pickupStartTime,

            LocalTime pickupEndTime,

            PickupType pickupType,

            String startedByStaffName,

            LocalDateTime createdAt,

            List<Item> items
    ) {
    }


    public record Item(

            String productName,

            Integer quantity,

            Integer displayOrder
    ) {
    }
}