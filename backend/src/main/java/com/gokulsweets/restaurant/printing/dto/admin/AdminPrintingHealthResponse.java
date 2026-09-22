package com.gokulsweets.restaurant.printing.dto.admin;

import com.gokulsweets.restaurant.printing.enums.PrintAgentHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import java.time.LocalDateTime;

public record AdminPrintingHealthResponse(

        Long branchId,

        PrinterStation station,

        Agent agent,

        Printer printer,

        Queue queue,

        Activity activity
) {

    public record Agent(

            String agentId,

            PrintAgentHealthStatus status,

            LocalDateTime firstSeenAt,

            LocalDateTime lastSeenAt,

            Long heartbeatCount,

            Long secondsSinceHeartbeat
    ) {
    }


    public record Printer(

            Long printerId,

            String code,

            String name,

            String protocol,

            String host,

            Integer port,

            Boolean active,

            PrinterHealthStatus status
    ) {
    }


    public record Queue(

            long queued,

            long claimed,

            long failed,

            long permanentlyFailed
    ) {
    }


    public record Activity(

            LocalDateTime lastSuccessfulPrintAt,

            Long lastSuccessfulPrintJobId,

            LocalDateTime lastFailureAt,

            Long lastFailedPrintJobId,

            String lastFailureCode,

            String lastFailureMessage
    ) {
    }
}