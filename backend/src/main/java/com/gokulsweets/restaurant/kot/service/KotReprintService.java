package com.gokulsweets.restaurant.kot.service;

import com.gokulsweets.restaurant.kot.dto.AdminKotReprintResponse;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.service.PrintJobService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates kot reprint operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KotReprintService {

    private final KotRepository kotRepository;

    private final PrintJobService printJobService;

    private final StaffAuthorizationService staffAuthorizationService;

    /*
     * =========================================================
     * QUEUE KOT REPRINT
     * =========================================================
     */
    /**
     * Queues reprint.
     *
     * @param kotNumber the kot number
     * @return the queue reprint result
     */
    @Transactional
    public AdminKotReprintResponse queueReprint(String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotReprintService.class, "queueReprint(String)");
        try {
            /*
             * Keep the same permission currently used by the
             * browser/manual KOT print action.
             *
             * A dedicated PRINT_MANAGE permission can be introduced
             * later without changing the durable print architecture.
             */
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            String normalizedKotNumber = normalizeKotNumber(kotNumber);
            Kot kot =
                    kotRepository
                            .findDetailedByKotNumber(normalizedKotNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "KOT reprint requested but KOT does not exist:"
                                                        + " kotNumber={}",
                                                normalizedKotNumber);
                                        return new IllegalArgumentException("KOT does not exist.");
                                    });
            Long branchId = kot.getBranch().getId();
            staffAuthorizationService.requireBranchAccess(branchId);
            PrintJob printJob = printJobService.createKotReprintJob(kot);
            log.info(
                    "Admin KOT reprint queued: printJobId={}, kotId={}, kotNumber={},"
                            + " orderNumber={}, branchId={}",
                    printJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getOrder().getOrderNumber(),
                    branchId);
            return toResponse(printJob);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotReprintService.class, "queueReprint(String)");
        }
    }

    /*
     * =========================================================
     * RESPONSE
     * =========================================================
     */
    /**
     * Tos response.
     *
     * @param printJob the print job
     * @return the to response result
     */
    private AdminKotReprintResponse toResponse(PrintJob printJob) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotReprintService.class, "toResponse(PrintJob)");
        try {
            Kot kot = printJob.getKot();
            return new AdminKotReprintResponse(
                    printJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getOrder().getOrderNumber(),
                    kot.getBranch().getId(),
                    printJob.getPurpose(),
                    printJob.getStation(),
                    printJob.getStatus(),
                    printJob.getPrinter() == null ? null : printJob.getPrinter().getId(),
                    printJob.getPrinter() == null ? null : printJob.getPrinter().getName(),
                    printJob.getAttemptCount(),
                    printJob.getMaxAttempts(),
                    printJob.getQueuedAt(),
                    printJob.getNextAttemptAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotReprintService.class, "toResponse(PrintJob)");
        }
    }

    /*
     * =========================================================
     * NORMALIZATION
     * =========================================================
     */
    /**
     * Normalizes kot number.
     *
     * @param kotNumber the kot number
     * @return the normalize kot number result
     */
    private String normalizeKotNumber(String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotReprintService.class, "normalizeKotNumber(String)");
        try {
            if (kotNumber == null || kotNumber.isBlank()) {
                throw new IllegalArgumentException("KOT number is required.");
            }
            return kotNumber.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    KotReprintService.class,
                    "normalizeKotNumber(String)");
        }
    }
}
