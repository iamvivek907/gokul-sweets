package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintingHealthResponse;
import com.gokulsweets.restaurant.printing.entity.PrintAgentHeartbeat;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.entity.PrinterDevice;
import com.gokulsweets.restaurant.printing.enums.PrintAgentHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterHealthStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.repository.PrintAgentHeartbeatRepository;
import com.gokulsweets.restaurant.printing.repository.PrinterDeviceRepository;
import com.gokulsweets.restaurant.printing.repository.PrintingHealthRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Coordinates admin printing health operations. */
@Service
@RequiredArgsConstructor
public class AdminPrintingHealthService {

    private static final long ONLINE_SECONDS =
            AppConstant.ADMIN_PRINTING_HEALTH_SERVICE_ONLINE_SECONDS;

    private static final long STALE_SECONDS =
            AppConstant.ADMIN_PRINTING_HEALTH_SERVICE_STALE_SECONDS;

    private final PrintAgentHeartbeatRepository heartbeatRepository;

    private final PrinterDeviceRepository printerDeviceRepository;

    private final PrintingHealthRepository healthRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    private final ApplicationClock applicationClock;

    /**
     * Returns health.
     *
     * @param branchId the branch id
     * @param station the station
     * @return the get health result
     */
    @Transactional(readOnly = true)
    public AdminPrintingHealthResponse getHealth(Long branchId, PrinterStation station) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintingHealthService.class, "getHealth(Long,PrinterStation)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            staffAuthorizationService.requireBranchAccess(branchId);
            LocalDateTime now = applicationClock.now();
            Optional<PrintAgentHeartbeat> heartbeat =
                    heartbeatRepository.findFirstByBranchIdAndStationOrderByLastSeenAtDesc(
                            branchId, station);
            AdminPrintingHealthResponse.Agent agent = buildAgentHealth(heartbeat, now);
            Optional<PrinterDevice> configuredPrinter =
                    printerDeviceRepository.findFirstByBranchIdAndStationAndActiveTrueOrderByIdAsc(
                            branchId, station);
            long queued =
                    healthRepository.countByBranchIdAndStationAndStatus(
                            branchId, station, PrintJobStatus.QUEUED);
            long claimed =
                    healthRepository.countByBranchIdAndStationAndStatus(
                            branchId, station, PrintJobStatus.CLAIMED);
            long failed =
                    healthRepository.countByBranchIdAndStationAndStatus(
                            branchId, station, PrintJobStatus.FAILED);
            long permanentFailures = healthRepository.countPermanentFailures(branchId, station);
            PrinterHealthStatus printerHealthStatus =
                    determinePrinterHealth(
                            configuredPrinter, agent.status(), failed, permanentFailures);
            AdminPrintingHealthResponse.Printer printer =
                    buildPrinterHealth(configuredPrinter, printerHealthStatus);
            Optional<PrintJob> lastSuccessful =
                    healthRepository.findFirstByBranchIdAndStationAndStatusOrderByPrintedAtDesc(
                            branchId, station, PrintJobStatus.PRINTED);
            Optional<PrintJob> lastFailure =
                    healthRepository.findFirstByBranchIdAndStationAndStatusOrderByFailedAtDesc(
                            branchId, station, PrintJobStatus.FAILED);
            AdminPrintingHealthResponse.Queue queue =
                    new AdminPrintingHealthResponse.Queue(
                            queued, claimed, failed, permanentFailures);
            AdminPrintingHealthResponse.Activity activity =
                    new AdminPrintingHealthResponse.Activity(
                            lastSuccessful.map(PrintJob::getPrintedAt).orElse(null),
                            lastSuccessful.map(PrintJob::getId).orElse(null),
                            lastFailure.map(PrintJob::getFailedAt).orElse(null),
                            lastFailure.map(PrintJob::getId).orElse(null),
                            lastFailure.map(PrintJob::getLastErrorCode).orElse(null),
                            lastFailure.map(PrintJob::getLastErrorMessage).orElse(null));
            return new AdminPrintingHealthResponse(
                    branchId, station, agent, printer, queue, activity);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintingHealthService.class,
                    "getHealth(Long,PrinterStation)");
        }
    }

    /*
     * =========================================================
     * AGENT
     * =========================================================
     */
    /**
     * Builds agent health.
     *
     * @param heartbeat the heartbeat
     * @param now the now
     * @return the build agent health result
     */
    private AdminPrintingHealthResponse.Agent buildAgentHealth(
            Optional<PrintAgentHeartbeat> heartbeat, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintingHealthService.class,
                        "buildAgentHealth(Optional<PrintAgentHeartbeat>,LocalDateTime)");
        try {
            if (heartbeat.isEmpty()) {
                return new AdminPrintingHealthResponse.Agent(
                        null, PrintAgentHealthStatus.OFFLINE, null, null, 0L, null);
            }
            PrintAgentHeartbeat record = heartbeat.get();
            long seconds = Math.max(0, Duration.between(record.getLastSeenAt(), now).getSeconds());
            PrintAgentHealthStatus status;
            if (seconds <= ONLINE_SECONDS) {
                status = PrintAgentHealthStatus.ONLINE;
            } else if (seconds <= STALE_SECONDS) {
                status = PrintAgentHealthStatus.STALE;
            } else {
                status = PrintAgentHealthStatus.OFFLINE;
            }
            return new AdminPrintingHealthResponse.Agent(
                    record.getAgentId(),
                    status,
                    record.getFirstSeenAt(),
                    record.getLastSeenAt(),
                    record.getHeartbeatCount(),
                    seconds);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintingHealthService.class,
                    "buildAgentHealth(Optional<PrintAgentHeartbeat>,LocalDateTime)");
        }
    }

    /*
     * =========================================================
     * PRINTER
     * =========================================================
     */
    /**
     * Determines printer health.
     *
     * @param printer the printer
     * @param agentStatus the agent status
     * @param failed the failed
     * @param permanentFailures the permanent failures
     * @return the determine printer health result
     */
    private PrinterHealthStatus determinePrinterHealth(
            Optional<PrinterDevice> printer,
            PrintAgentHealthStatus agentStatus,
            long failed,
            long permanentFailures) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintingHealthService.class,
                        "determinePrinterHealth(Optional<PrinterDevice>,PrintAgentHealthStatus,long,long)");
        try {
            if (printer.isEmpty()) {
                return PrinterHealthStatus.NOT_CONFIGURED;
            }
            if (agentStatus == PrintAgentHealthStatus.OFFLINE) {
                return PrinterHealthStatus.OFFLINE;
            }
            if (agentStatus == PrintAgentHealthStatus.STALE) {
                return PrinterHealthStatus.UNKNOWN;
            }
            if (permanentFailures > 0) {
                return PrinterHealthStatus.DEGRADED;
            }
            if (failed > 0) {
                return PrinterHealthStatus.DEGRADED;
            }
            return PrinterHealthStatus.READY;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintingHealthService.class,
                    "determinePrinterHealth(Optional<PrinterDevice>,PrintAgentHealthStatus,long,long)");
        }
    }

    /**
     * Builds printer health.
     *
     * @param printer the printer
     * @param status the status
     * @return the build printer health result
     */
    private AdminPrintingHealthResponse.Printer buildPrinterHealth(
            Optional<PrinterDevice> printer, PrinterHealthStatus status) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintingHealthService.class,
                        "buildPrinterHealth(Optional<PrinterDevice>,PrinterHealthStatus)");
        try {
            if (printer.isEmpty()) {
                return new AdminPrintingHealthResponse.Printer(
                        null, null, null, null, null, null, false, status);
            }
            PrinterDevice device = printer.get();
            return new AdminPrintingHealthResponse.Printer(
                    device.getId(),
                    device.getCode(),
                    device.getName(),
                    device.getProtocol().name(),
                    device.getHost(),
                    device.getPort(),
                    device.isActive(),
                    status);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintingHealthService.class,
                    "buildPrinterHealth(Optional<PrinterDevice>,PrinterHealthStatus)");
        }
    }
}
