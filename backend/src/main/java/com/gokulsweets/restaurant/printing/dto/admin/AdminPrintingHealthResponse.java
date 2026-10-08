package com.gokulsweets.restaurant.printing.dto.admin;

import com.gokulsweets.restaurant.printing.enums.PrintAgentHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

/** Immutable admin printing health response data contract. */
public record AdminPrintingHealthResponse(
        Long branchId,
        PrinterStation station,
        Agent agent,
        Printer printer,
        Queue queue,
        Activity activity) {

    /** Immutable agent data contract. */
    public record Agent(
            String agentId,
            PrintAgentHealthStatus status,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            Long heartbeatCount,
            Long secondsSinceHeartbeat) {}

    /** Immutable printer data contract. */
    public record Printer(
            Long printerId,
            String code,
            String name,
            String protocol,
            String host,
            Integer port,
            Boolean active,
            PrinterHealthStatus status) {}

    /** Immutable queue data contract. */
    public record Queue(long queued, long claimed, long failed, long permanentlyFailed) {}

    /** Immutable activity data contract. */
    public record Activity(
            LocalDateTime lastSuccessfulPrintAt,
            Long lastSuccessfulPrintJobId,
            LocalDateTime lastFailureAt,
            Long lastFailedPrintJobId,
            String lastFailureCode,
            String lastFailureMessage) {}
}
