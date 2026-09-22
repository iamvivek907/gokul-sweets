package com.gokulsweets.restaurant.kot.dto;

import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

public record AdminKotReprintResponse(

        Long printJobId,

        Long kotId,

        String kotNumber,

        String orderNumber,

        Long branchId,

        PrintJobPurpose purpose,

        PrinterStation station,

        PrintJobStatus status,

        Long printerId,

        String printerName,

        Integer attemptCount,

        Integer maxAttempts,

        LocalDateTime queuedAt,

        LocalDateTime nextAttemptAt
) {
}