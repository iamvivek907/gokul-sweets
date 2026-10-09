package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

/**
 * Read-only status for reconciling an acknowledgement whose response was lost.
 *
 * @param printJobId the print job id
 * @param branchId the verified branch id
 * @param station the verified printer station
 * @param status the current job status
 */
public record PrintAgentJobStatusResponse(
        Long printJobId, Long branchId, PrinterStation station, PrintJobStatus status) {}
