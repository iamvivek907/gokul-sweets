package com.gokulsweets.restaurant.printing.dto.admin;

import com.gokulsweets.restaurant.printing.enums.PrintAgentHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

/**
 * Immutable admin printing health response data contract.
 *
 * @param branchId the branch id
 * @param station the station
 * @param agent the agent
 * @param printer the printer
 * @param queue the queue
 * @param activity the activity
 */
public record AdminPrintingHealthResponse(
        Long branchId,
        PrinterStation station,
        Agent agent,
        Printer printer,
        Queue queue,
        Activity activity) {

    /**
     * Immutable agent data contract.
     *
     * @param agentId the agent id
     * @param status the status
     * @param firstSeenAt the first seen at
     * @param lastSeenAt the last seen at
     * @param heartbeatCount the heartbeat count
     * @param secondsSinceHeartbeat the seconds since heartbeat
     */
    public record Agent(
            String agentId,
            PrintAgentHealthStatus status,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            Long heartbeatCount,
            Long secondsSinceHeartbeat) {}

    /**
     * Immutable printer data contract.
     *
     * @param printerId the printer id
     * @param code the code
     * @param name the name
     * @param protocol the protocol
     * @param host the host
     * @param port the port
     * @param active the active
     * @param status the status
     */
    public record Printer(
            Long printerId,
            String code,
            String name,
            String protocol,
            String host,
            Integer port,
            Boolean active,
            PrinterHealthStatus status) {}

    /**
     * Immutable queue data contract.
     *
     * @param queued the queued
     * @param claimed the claimed
     * @param failed the failed
     * @param permanentlyFailed the permanently failed
     */
    public record Queue(long queued, long claimed, long failed, long permanentlyFailed) {}

    /**
     * Immutable activity data contract.
     *
     * @param lastSuccessfulPrintAt the last successful print at
     * @param lastSuccessfulPrintJobId the last successful print job id
     * @param lastFailureAt the last failure at
     * @param lastFailedPrintJobId the last failed print job id
     * @param lastFailureCode the last failure code
     * @param lastFailureMessage the last failure message
     */
    public record Activity(
            LocalDateTime lastSuccessfulPrintAt,
            Long lastSuccessfulPrintJobId,
            LocalDateTime lastFailureAt,
            Long lastFailedPrintJobId,
            String lastFailureCode,
            String lastFailureMessage) {}
}
