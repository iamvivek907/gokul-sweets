package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.delivery.DeliveryOrderWindowLookup;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.entity.KotItem;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentFailedRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentPrintedRequest;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.entity.PrinterDevice;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.repository.PrintAgentHeartbeatRepository;
import com.gokulsweets.restaurant.printing.repository.PrintJobClaimRepository;
import com.gokulsweets.restaurant.printing.repository.PrintJobRepository;
import com.gokulsweets.restaurant.printing.repository.PrinterDeviceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Coordinates print agent operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrintAgentService {

    private static final long CLAIM_LEASE_SECONDS =
            AppConstant.PRINT_AGENT_SERVICE_CLAIM_LEASE_SECONDS;

    private static final long BASE_RETRY_SECONDS =
            AppConstant.PRINT_AGENT_SERVICE_BASE_RETRY_SECONDS;

    private static final long MAX_RETRY_SECONDS = AppConstant.PRINT_AGENT_SERVICE_MAX_RETRY_SECONDS;

    private final PrintAgentHeartbeatRepository printAgentHeartbeatRepository;

    private final PrintJobClaimRepository printJobClaimRepository;

    private final PrintJobRepository printJobRepository;

    private final PrinterDeviceRepository printerDeviceRepository;

    private final KotRepository kotRepository;

    private final DeliveryOrderWindowLookup deliveryWindows;

    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */
    /**
     * Updates the heartbeat for print agent data and returns the {@code
     * PrintAgentHeartbeatResponse} result.
     *
     * <p>Delegates to {@code printAgentHeartbeatRepository.upsertHeartbeat(...)}.
     *
     * @param request the request supplied to this method
     * @return the {@code PrintAgentHeartbeatResponse} result
     */
    @Transactional
    public PrintAgentHeartbeatResponse heartbeat(PrintAgentHeartbeatRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentService.class, "heartbeat(PrintAgentHeartbeatRequest)");
        try {
            String agentId = normalizeAgentId(request.agentId());
            LocalDateTime now = LocalDateTime.now();
            printAgentHeartbeatRepository.upsertHeartbeat(
                    request.branchId(), agentId, request.station().name(), now);
            log.debug(
                    "Print-agent heartbeat recorded: branchId={}, agentId={}, station={},"
                            + " heartbeatAt={}",
                    request.branchId(),
                    agentId,
                    request.station(),
                    now);
            return new PrintAgentHeartbeatResponse(
                    agentId, request.branchId(), request.station().name(), now, "OK");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "heartbeat(PrintAgentHeartbeatRequest)");
        }
    }

    /*
     * =========================================================
     * CLAIM NEXT PRINT JOB
     * =========================================================
     */
    /**
     * Claims next.
     *
     * @param request the request
     * @return the claim next result
     */
    @Transactional
    public Optional<PrintAgentClaimResponse> claimNext(PrintAgentClaimRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "claimNext(PrintAgentClaimRequest)");
        try {
            String agentId = normalizeAgentId(request.agentId());
            /*
             * A physical printer must currently be configured before
             * the agent can claim work.
             *
             * Print jobs themselves remain safely QUEUED even when
             * no printer exists.
             */
            Optional<PrinterDevice> availablePrinter =
                    printerDeviceRepository.findFirstByBranchIdAndStationAndActiveTrueOrderByIdAsc(
                            request.branchId(), request.station());
            if (availablePrinter.isEmpty()) {
                log.debug(
                        "No active printer configured for print-agent claim: branchId={},"
                                + " station={}, agentId={}",
                        request.branchId(),
                        request.station(),
                        agentId);
                return Optional.empty();
            }
            LocalDateTime now = LocalDateTime.now();
            Optional<PrintJob> optionalJob =
                    printJobClaimRepository.findNextClaimableJobForUpdate(
                            request.branchId(), request.station(), now);
            if (optionalJob.isEmpty()) {
                return Optional.empty();
            }
            PrintJob printJob = optionalJob.get();
            PrinterDevice printer = availablePrinter.get();
            String claimToken = UUID.randomUUID().toString();
            LocalDateTime claimExpiresAt = now.plusSeconds(CLAIM_LEASE_SECONDS);
            int currentAttemptCount =
                    printJob.getAttemptCount() == null ? 0 : printJob.getAttemptCount();
            printJob.setPrinter(printer);
            printJob.setStatus(PrintJobStatus.CLAIMED);
            printJob.setClaimToken(claimToken);
            printJob.setClaimedByAgent(agentId);
            printJob.setClaimedAt(now);
            printJob.setClaimExpiresAt(claimExpiresAt);
            printJob.setAttemptCount(currentAttemptCount + 1);
            if (printJob.getFirstAttemptAt() == null) {
                printJob.setFirstAttemptAt(now);
            }
            printJob.setLastAttemptAt(now);
            printJob.setFailedAt(null);
            printJob.setLastErrorCode(null);
            printJob.setLastErrorMessage(null);
            printJob.setNextAttemptAt(null);
            PrintJob savedJob = printJobRepository.saveAndFlush(printJob);
            PrintAgentClaimResponse response =
                    buildClaimResponse(savedJob, claimToken, claimExpiresAt, printer);
            log.info(
                    "Print job claimed: printJobId={}, kotId={}, branchId={}, station={},"
                            + " printerId={}, agentId={}, attemptCount={}, leaseExpiresAt={}",
                    savedJob.getId(),
                    savedJob.getKot().getId(),
                    savedJob.getBranch().getId(),
                    savedJob.getStation(),
                    printer.getId(),
                    agentId,
                    savedJob.getAttemptCount(),
                    claimExpiresAt);
            return Optional.of(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "claimNext(PrintAgentClaimRequest)");
        }
    }

    /*
     * =========================================================
     * ACKNOWLEDGE PRINTED
     * =========================================================
     */
    /**
     * Marks printed.
     *
     * @param printJobId the print job id
     * @param request the request
     */
    @Transactional
    public void markPrinted(Long printJobId, PrintAgentPrintedRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentService.class, "markPrinted(Long,PrintAgentPrintedRequest)");
        try {
            PrintJob printJob = getClaimedJob(printJobId, request.agentId(), request.claimToken());
            LocalDateTime now = LocalDateTime.now();
            printJob.setStatus(PrintJobStatus.PRINTED);
            printJob.setPrintedAt(now);
            clearClaim(printJob);
            printJob.setNextAttemptAt(null);
            printJob.setFailedAt(null);
            printJob.setLastErrorCode(null);
            printJob.setLastErrorMessage(null);
            printJobRepository.saveAndFlush(printJob);
            log.info(
                    "Print job acknowledged as printed: printJobId={}, kotId={}, branchId={},"
                            + " agentId={}, attemptCount={}",
                    printJob.getId(),
                    printJob.getKot().getId(),
                    printJob.getBranch().getId(),
                    normalizeAgentId(request.agentId()),
                    printJob.getAttemptCount());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "markPrinted(Long,PrintAgentPrintedRequest)");
        }
    }

    /*
     * =========================================================
     * ACKNOWLEDGE FAILED
     * =========================================================
     */
    /**
     * Marks failed.
     *
     * @param printJobId the print job id
     * @param request the request
     */
    @Transactional
    public void markFailed(Long printJobId, PrintAgentFailedRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentService.class, "markFailed(Long,PrintAgentFailedRequest)");
        try {
            PrintJob printJob = getClaimedJob(printJobId, request.agentId(), request.claimToken());
            LocalDateTime now = LocalDateTime.now();
            int attemptCount = printJob.getAttemptCount() == null ? 0 : printJob.getAttemptCount();
            int maxAttempts = printJob.getMaxAttempts() == null ? 1 : printJob.getMaxAttempts();
            printJob.setStatus(PrintJobStatus.FAILED);
            printJob.setFailedAt(now);
            printJob.setLastErrorCode(normalizeErrorCode(request.errorCode()));
            printJob.setLastErrorMessage(normalizeNullableText(request.errorMessage()));
            clearClaim(printJob);
            if (attemptCount < maxAttempts) {
                long retrySeconds = calculateRetryDelaySeconds(attemptCount);
                printJob.setNextAttemptAt(now.plusSeconds(retrySeconds));
                log.warn(
                        "Print job failed and will retry: printJobId={}, kotId={}, attemptCount={},"
                                + " maxAttempts={}, retryInSeconds={}, errorCode={}",
                        printJob.getId(),
                        printJob.getKot().getId(),
                        attemptCount,
                        maxAttempts,
                        retrySeconds,
                        request.errorCode());
            } else {
                /*
                 * Final failure.
                 *
                 * It remains FAILED and requires staff/admin
                 * intervention or a future explicit retry action.
                 */
                printJob.setNextAttemptAt(null);
                log.error(
                        "Print job reached maximum attempts: printJobId={}, kotId={},"
                                + " attemptCount={}, maxAttempts={}, errorCode={}",
                        printJob.getId(),
                        printJob.getKot().getId(),
                        attemptCount,
                        maxAttempts,
                        request.errorCode());
            }
            printJobRepository.saveAndFlush(printJob);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "markFailed(Long,PrintAgentFailedRequest)");
        }
    }

    /*
     * =========================================================
     * CLAIM RESPONSE
     * =========================================================
     */
    /**
     * Builds claim response.
     *
     * @param printJob the print job
     * @param claimToken the claim token
     * @param claimExpiresAt the claim expires at
     * @param printer the printer
     * @return the build claim response result
     */
    private PrintAgentClaimResponse buildClaimResponse(
            PrintJob printJob,
            String claimToken,
            LocalDateTime claimExpiresAt,
            PrinterDevice printer) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentService.class,
                        "buildClaimResponse(PrintJob,String,LocalDateTime,PrinterDevice)");
        try {
            Kot kot =
                    kotRepository
                            .findDetailedByKotNumber(printJob.getKot().getKotNumber())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "The KOT for the print job no longer exists."));
            PickupSlot pickupSlot = kot.getOrder().getPickupSlot();
            var deliveryWindow =
                    kot.getOrder().getFulfillmentType() == FulfillmentType.DELIVERY
                            ? deliveryWindows.require(kot.getOrder())
                            : null;
            if (pickupSlot == null && deliveryWindow == null) {
                throw new IllegalStateException(
                        "Pickup slot is unavailable for the KOT print job.");
            }
            List<PrintAgentClaimResponse.Item> items =
                    kot.getItems().stream().map(this::toPrintItem).toList();
            PrintAgentClaimResponse.Printer printerResponse =
                    new PrintAgentClaimResponse.Printer(
                            printer.getId(),
                            printer.getCode(),
                            printer.getName(),
                            printer.getStation(),
                            printer.getProtocol(),
                            printer.getHost(),
                            printer.getPort(),
                            printer.getPaperWidthMm(),
                            printer.isAutoCut());
            PrintAgentClaimResponse.KotPayload kotResponse =
                    new PrintAgentClaimResponse.KotPayload(
                            kot.getId(),
                            kot.getKotNumber(),
                            kot.getOrder().getOrderNumber(),
                            kot.getOrder().getCustomerOrderNumber(),
                            kot.getBranch().getName(),
                            kot.getBranch().getAddress(),
                            pickupSlot == null ? null : pickupSlot.getSlotDate(),
                            pickupSlot == null ? null : pickupSlot.getStartTime(),
                            pickupSlot == null ? null : pickupSlot.getEndTime(),
                            kot.getOrder().getPickupType(),
                            kot.getStartedByStaffName(),
                            kot.getCreatedAt(),
                            items,
                            kot.getOrder().getFulfillmentType(),
                            deliveryWindow == null ? null : deliveryWindow.date(),
                            deliveryWindow == null ? null : deliveryWindow.start(),
                            deliveryWindow == null ? null : deliveryWindow.end());
            return new PrintAgentClaimResponse(
                    printJob.getId(),
                    claimToken,
                    claimExpiresAt,
                    printJob.getCopies(),
                    printerResponse,
                    kotResponse);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "buildClaimResponse(PrintJob,String,LocalDateTime,PrinterDevice)");
        }
    }

    /**
     * Tos print item.
     *
     * @param item the item
     * @return the to print item result
     */
    private PrintAgentClaimResponse.Item toPrintItem(KotItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "toPrintItem(KotItem)");
        try {
            String label;
            if ("WEIGHT".equals(item.getSaleMode()) && item.getWeightGrams() != null) {
                label =
                        item.getWeightGrams() >= 1000
                                ? java.math.BigDecimal.valueOf(item.getWeightGrams())
                                                .divide(java.math.BigDecimal.valueOf(1000))
                                                .stripTrailingZeros()
                                                .toPlainString()
                                        + " kg"
                                : item.getWeightGrams() + " g";
            } else if ("UNIT".equals(item.getSaleMode())) label = item.getQuantity() + " pcs";
            else label = item.getQuantity() + " (confirm unit)";
            // Older print agents still consume name + quantity. Include the weight in
            // the name too so those agents cannot silently print 1 instead of 2 kg.
            String name =
                    "UNIT".equals(item.getSaleMode())
                            ? item.getProductName()
                            : item.getProductName() + " · " + label;
            return new PrintAgentClaimResponse.Item(
                    name,
                    item.getQuantity(),
                    item.getDisplayOrder(),
                    item.getSaleMode(),
                    item.getWeightGrams(),
                    label);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PrintAgentService.class, "toPrintItem(KotItem)");
        }
    }

    /*
     * =========================================================
     * CLAIM VALIDATION
     * =========================================================
     */
    /**
     * Returns claimed job.
     *
     * @param printJobId the print job id
     * @param agentId the agent id
     * @param claimToken the claim token
     * @return the get claimed job result
     */
    private PrintJob getClaimedJob(Long printJobId, String agentId, String claimToken) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "getClaimedJob(Long,String,String)");
        try {
            PrintJob printJob =
                    printJobRepository
                            .findById(printJobId)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Print job does not exist."));
            if (printJob.getStatus() != PrintJobStatus.CLAIMED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The print job is no longer claimed.");
            }
            String normalizedAgentId = normalizeAgentId(agentId);
            if (printJob.getClaimedByAgent() == null
                    || !printJob.getClaimedByAgent().equals(normalizedAgentId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The print job is claimed by another agent.");
            }
            if (printJob.getClaimToken() == null
                    || claimToken == null
                    || !printJob.getClaimToken().equals(claimToken.trim())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The print-job claim token is invalid.");
            }
            return printJob;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "getClaimedJob(Long,String,String)");
        }
    }

    /*
     * =========================================================
     * RETRY
     * =========================================================
     */
    /**
     * Calculates retry delay seconds.
     *
     * @param attemptCount the attempt count
     * @return the calculate retry delay seconds result
     */
    private long calculateRetryDelaySeconds(int attemptCount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "calculateRetryDelaySeconds(int)");
        try {
            int exponent = Math.max(0, attemptCount - 1);
            exponent = Math.min(exponent, 5);
            long delay = BASE_RETRY_SECONDS * (1L << exponent);
            return Math.min(delay, MAX_RETRY_SECONDS);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "calculateRetryDelaySeconds(int)");
        }
    }

    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */
    /**
     * Clears claim.
     *
     * @param printJob the print job
     */
    private void clearClaim(PrintJob printJob) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "clearClaim(PrintJob)");
        try {
            printJob.setClaimToken(null);
            printJob.setClaimedByAgent(null);
            printJob.setClaimedAt(null);
            printJob.setClaimExpiresAt(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PrintAgentService.class, "clearClaim(PrintJob)");
        }
    }

    /**
     * Normalizes agent id.
     *
     * @param agentId the agent id
     * @return the normalize agent id result
     */
    private String normalizeAgentId(String agentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "normalizeAgentId(String)");
        try {
            if (agentId == null || agentId.isBlank()) {
                throw new IllegalArgumentException("Agent ID is required.");
            }
            return agentId.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PrintAgentService.class, "normalizeAgentId(String)");
        }
    }

    /**
     * Normalizes error code.
     *
     * @param errorCode the error code
     * @return the normalize error code result
     */
    private String normalizeErrorCode(String errorCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "normalizeErrorCode(String)");
        try {
            if (errorCode == null || errorCode.isBlank()) {
                return "UNKNOWN_PRINT_ERROR";
            }
            return errorCode.trim().toUpperCase();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "normalizeErrorCode(String)");
        }
    }

    /**
     * Normalizes nullable text.
     *
     * @param value the value
     * @return the normalize nullable text result
     */
    private String normalizeNullableText(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentService.class, "normalizeNullableText(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentService.class,
                    "normalizeNullableText(String)");
        }
    }
}
