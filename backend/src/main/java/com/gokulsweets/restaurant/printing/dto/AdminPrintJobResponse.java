package com.gokulsweets.restaurant.printing.dto.admin;

import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrintJobType;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

public record AdminPrintJobResponse(

        Long id,

        Long branchId,

        String branchName,

        Long kotId,

        String kotNumber,

        String orderNumber,

        Long printerId,

        String printerName,

        PrintJobType jobType,

        PrintJobPurpose purpose,

        PrinterStation station,

        PrintJobStatus status,

        Integer copies,

        Integer attemptCount,

        Integer maxAttempts,

        String claimedByAgent,

        LocalDateTime claimedAt,

        LocalDateTime claimExpiresAt,

        LocalDateTime queuedAt,

        LocalDateTime firstAttemptAt,

        LocalDateTime lastAttemptAt,

        LocalDateTime printedAt,

        LocalDateTime failedAt,

        LocalDateTime nextAttemptAt,

        String lastErrorCode,

        String lastErrorMessage
) {
}