package com.gokulsweets.restaurant.kot.dto;

import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

/**
 * Immutable admin kot reprint response data contract.
 *
 * @param printJobId the print job id
 * @param kotId the kot id
 * @param kotNumber the kot number
 * @param orderNumber the order number
 * @param branchId the branch id
 * @param purpose the purpose
 * @param station the station
 * @param status the status
 * @param printerId the printer id
 * @param printerName the printer name
 * @param attemptCount the attempt count
 * @param maxAttempts the max attempts
 * @param queuedAt the queued at
 * @param nextAttemptAt the next attempt at
 */
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
        LocalDateTime nextAttemptAt) {}
