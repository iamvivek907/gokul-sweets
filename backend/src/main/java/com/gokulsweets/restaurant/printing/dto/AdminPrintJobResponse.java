package com.gokulsweets.restaurant.printing.dto.admin;

import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrintJobType;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

/**
 * Immutable admin print job response data contract.
 *
 * @param id the id
 * @param branchId the branch id
 * @param branchName the branch name
 * @param kotId the kot id
 * @param kotNumber the kot number
 * @param orderNumber the order number
 * @param customerOrderNumber the customer order number
 * @param printerId the printer id
 * @param printerName the printer name
 * @param jobType the job type
 * @param purpose the purpose
 * @param station the station
 * @param status the status
 * @param copies the copies
 * @param attemptCount the attempt count
 * @param maxAttempts the max attempts
 * @param claimedByAgent the claimed by agent
 * @param claimedAt the claimed at
 * @param claimExpiresAt the claim expires at
 * @param queuedAt the queued at
 * @param firstAttemptAt the first attempt at
 * @param lastAttemptAt the last attempt at
 * @param printedAt the printed at
 * @param failedAt the failed at
 * @param nextAttemptAt the next attempt at
 * @param lastErrorCode the last error code
 * @param lastErrorMessage the last error message
 */
public record AdminPrintJobResponse(
        Long id,
        Long branchId,
        String branchName,
        Long kotId,
        String kotNumber,
        String orderNumber,
        Long customerOrderNumber,
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
        String lastErrorMessage) {}
