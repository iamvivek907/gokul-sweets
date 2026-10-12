package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobCountsResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobPageResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobResponse;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.repository.AdminPrintJobRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** Coordinates admin print job operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPrintJobService {

    private static final int DEFAULT_PAGE_SIZE =
            AppConstant.ADMIN_PRINT_JOB_SERVICE_DEFAULT_PAGE_SIZE;

    private static final int MAX_PAGE_SIZE = AppConstant.ADMIN_PRINT_JOB_SERVICE_MAX_PAGE_SIZE;

    private final AdminPrintJobRepository printJobRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /*
     * =========================================================
     * LIST
     * =========================================================
     */
    /**
     * Returns jobs.
     *
     * @param branchId the branch id
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get jobs result
     */
    @Transactional(readOnly = true)
    public AdminPrintJobPageResponse getJobs(
            Long branchId, PrintJobStatus status, Integer page, Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintJobService.class, "getJobs(Long,PrintJobStatus,Integer,Integer)");
        try {
            requireViewAccess(branchId);
            int safePage = page == null ? 0 : Math.max(0, page);
            int safeSize =
                    size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
            Page<PrintJob> result =
                    printJobRepository.findAdminJobs(
                            branchId, status, PageRequest.of(safePage, safeSize));
            List<AdminPrintJobResponse> jobs =
                    result.getContent().stream().map(this::toResponse).toList();
            return new AdminPrintJobPageResponse(
                    jobs,
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintJobService.class,
                    "getJobs(Long,PrintJobStatus,Integer,Integer)");
        }
    }

    /*
     * =========================================================
     * COUNTS
     * =========================================================
     */
    /**
     * Returns counts.
     *
     * @param branchId the branch id
     * @return the get counts result
     */
    @Transactional(readOnly = true)
    public AdminPrintJobCountsResponse getCounts(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobService.class, "getCounts(Long)");
        try {
            requireViewAccess(branchId);
            return new AdminPrintJobCountsResponse(
                    printJobRepository.countByBranchIdAndStatus(branchId, PrintJobStatus.QUEUED),
                    printJobRepository.countByBranchIdAndStatus(branchId, PrintJobStatus.CLAIMED),
                    printJobRepository.countByBranchIdAndStatus(branchId, PrintJobStatus.PRINTED),
                    printJobRepository.countByBranchIdAndStatus(branchId, PrintJobStatus.FAILED),
                    printJobRepository.countPermanentFailures(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrintJobService.class, "getCounts(Long)");
        }
    }

    /*
     * =========================================================
     * MANUAL RETRY
     * =========================================================
     */
    /**
     * Retries admin print job data and returns the {@code AdminPrintJobResponse} result.
     *
     * <p>Authorization checks include {@code PermissionName.ORDER_START_PREPARATION}.
     *
     * <p>Delegates to {@code printJobRepository.findForRetry(...)}, {@code
     * staffAuthorizationService.requirePermission(...)}, {@code
     * staffAuthorizationService.requireBranchAccess(...)}, {@code
     * printJobRepository.saveAndFlush(...)}.
     *
     * @param printJobId the print job id supplied to this method
     * @return the value of {@code toResponse(saved)}
     * @throws ResponseStatusException when the method rejects the request with {@code A printed job
     *     cannot be retried as the same print job.}; {@code The print job is currently claimed by a
     *     print agent.}
     */
    @Transactional
    public AdminPrintJobResponse retry(Long printJobId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobService.class, "retry(Long)");
        try {
            PrintJob printJob =
                    printJobRepository
                            .findForRetry(printJobId)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Print job does not exist."));
            Long branchId = printJob.getBranch().getId();
            /*
             * Retrying a KOT means sending kitchen work again.
             * Reuse the preparation permission for now rather than
             * introducing a new permission in the middle of this phase.
             */
            staffAuthorizationService.requirePermission(PermissionName.ORDER_START_PREPARATION);
            staffAuthorizationService.requireBranchAccess(branchId);
            if (printJob.getStatus() == PrintJobStatus.PRINTED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A printed job cannot be retried as the same print job.");
            }
            if (printJob.getStatus() == PrintJobStatus.CLAIMED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The print job is currently claimed by a print agent.");
            }
            LocalDateTime now = LocalDateTime.now();
            /*
             * Explicit staff retry resets the automatic attempt budget.
             */
            printJob.setAttemptCount(0);
            printJob.setStatus(PrintJobStatus.QUEUED);
            printJob.setNextAttemptAt(now);
            printJob.setFailedAt(null);
            printJob.setLastErrorCode(null);
            printJob.setLastErrorMessage(null);
            printJob.setClaimToken(null);
            printJob.setClaimedByAgent(null);
            printJob.setClaimedAt(null);
            printJob.setClaimExpiresAt(null);
            PrintJob saved = printJobRepository.saveAndFlush(printJob);
            log.info(
                    "Admin manually requeued print job: printJobId={}, kotId={}, branchId={}",
                    saved.getId(),
                    saved.getKot().getId(),
                    branchId);
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrintJobService.class, "retry(Long)");
        }
    }

    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */
    /**
     * Requires view access.
     *
     * @param branchId the branch id
     */
    private void requireViewAccess(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobService.class, "requireViewAccess(Long)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.ORDER_VIEW);
            staffAuthorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintJobService.class,
                    "requireViewAccess(Long)");
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
    private AdminPrintJobResponse toResponse(PrintJob printJob) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobService.class, "toResponse(PrintJob)");
        try {
            return new AdminPrintJobResponse(
                    printJob.getId(),
                    printJob.getBranch().getId(),
                    printJob.getBranch().getName(),
                    printJob.getKot().getId(),
                    printJob.getKot().getKotNumber(),
                    printJob.getKot().getOrder().getOrderNumber(),
                    printJob.getKot().getOrder().getCustomerOrderNumber(),
                    printJob.getPrinter() == null ? null : printJob.getPrinter().getId(),
                    printJob.getPrinter() == null ? null : printJob.getPrinter().getName(),
                    printJob.getJobType(),
                    printJob.getPurpose(),
                    printJob.getStation(),
                    printJob.getStatus(),
                    printJob.getCopies(),
                    printJob.getAttemptCount(),
                    printJob.getMaxAttempts(),
                    printJob.getClaimedByAgent(),
                    printJob.getClaimedAt(),
                    printJob.getClaimExpiresAt(),
                    printJob.getQueuedAt(),
                    printJob.getFirstAttemptAt(),
                    printJob.getLastAttemptAt(),
                    printJob.getPrintedAt(),
                    printJob.getFailedAt(),
                    printJob.getNextAttemptAt(),
                    printJob.getLastErrorCode(),
                    printJob.getLastErrorMessage());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrintJobService.class, "toResponse(PrintJob)");
        }
    }
}
