package com.gokulsweets.restaurant.printing.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPrintJobService {

    private static final int DEFAULT_PAGE_SIZE =
            20;

    private static final int MAX_PAGE_SIZE =
            100;


    private final AdminPrintJobRepository
            printJobRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    /*
     * =========================================================
     * LIST
     * =========================================================
     */

    @Transactional(readOnly = true)
    public AdminPrintJobPageResponse getJobs(
            Long branchId,
            PrintJobStatus status,
            Integer page,
            Integer size
    ) {

        requireViewAccess(
                branchId
        );


        int safePage =
                page == null
                        ? 0
                        : Math.max(
                        0,
                        page
                );


        int safeSize =
                size == null
                        ? DEFAULT_PAGE_SIZE
                        : Math.max(
                        1,
                        Math.min(
                                size,
                                MAX_PAGE_SIZE
                        )
                );


        Page<PrintJob> result =
                printJobRepository
                        .findAdminJobs(
                                branchId,
                                status,
                                PageRequest.of(
                                        safePage,
                                        safeSize
                                )
                        );


        List<AdminPrintJobResponse> jobs =
                result.getContent()
                        .stream()
                        .map(
                                this::toResponse
                        )
                        .toList();


        return new AdminPrintJobPageResponse(
                jobs,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }


    /*
     * =========================================================
     * COUNTS
     * =========================================================
     */

    @Transactional(readOnly = true)
    public AdminPrintJobCountsResponse getCounts(
            Long branchId
    ) {

        requireViewAccess(
                branchId
        );


        return new AdminPrintJobCountsResponse(

                printJobRepository
                        .countByBranchIdAndStatus(
                                branchId,
                                PrintJobStatus.QUEUED
                        ),

                printJobRepository
                        .countByBranchIdAndStatus(
                                branchId,
                                PrintJobStatus.CLAIMED
                        ),

                printJobRepository
                        .countByBranchIdAndStatus(
                                branchId,
                                PrintJobStatus.PRINTED
                        ),

                printJobRepository
                        .countByBranchIdAndStatus(
                                branchId,
                                PrintJobStatus.FAILED
                        ),

                printJobRepository
                        .countPermanentFailures(
                                branchId
                        )
        );
    }


    /*
     * =========================================================
     * MANUAL RETRY
     * =========================================================
     */

    @Transactional
    public AdminPrintJobResponse retry(
            Long printJobId
    ) {

        PrintJob printJob =
                printJobRepository
                        .findForRetry(
                                printJobId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Print job does not exist."
                                        )
                        );


        Long branchId =
                printJob.getBranch()
                        .getId();


        /*
         * Retrying a KOT means sending kitchen work again.
         * Reuse the preparation permission for now rather than
         * introducing a new permission in the middle of this phase.
         */
        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_START_PREPARATION
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        if (
                printJob.getStatus()
                        ==
                        PrintJobStatus.PRINTED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A printed job cannot be retried as the same print job."
            );
        }


        if (
                printJob.getStatus()
                        ==
                        PrintJobStatus.CLAIMED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The print job is currently claimed by a print agent."
            );
        }


        LocalDateTime now =
                LocalDateTime.now();


        /*
         * Explicit staff retry resets the automatic attempt budget.
         */
        printJob.setAttemptCount(
                0
        );


        printJob.setStatus(
                PrintJobStatus.QUEUED
        );


        printJob.setNextAttemptAt(
                now
        );


        printJob.setFailedAt(
                null
        );


        printJob.setLastErrorCode(
                null
        );


        printJob.setLastErrorMessage(
                null
        );


        printJob.setClaimToken(
                null
        );


        printJob.setClaimedByAgent(
                null
        );


        printJob.setClaimedAt(
                null
        );


        printJob.setClaimExpiresAt(
                null
        );


        PrintJob saved =
                printJobRepository
                        .saveAndFlush(
                                printJob
                        );


        log.info(
                "Admin manually requeued print job: printJobId={}, kotId={}, branchId={}",
                saved.getId(),
                saved.getKot()
                        .getId(),
                branchId
        );


        return toResponse(
                saved
        );
    }


    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */

    private void requireViewAccess(
            Long branchId
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_VIEW
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );
    }


    /*
     * =========================================================
     * RESPONSE
     * =========================================================
     */

    private AdminPrintJobResponse toResponse(
            PrintJob printJob
    ) {

        return new AdminPrintJobResponse(

                printJob.getId(),

                printJob.getBranch()
                        .getId(),

                printJob.getBranch()
                        .getName(),

                printJob.getKot()
                        .getId(),

                printJob.getKot()
                        .getKotNumber(),

                printJob.getKot()
                        .getOrder()
                        .getOrderNumber(),

                printJob.getPrinter()
                        == null
                        ? null
                        : printJob.getPrinter()
                        .getId(),

                printJob.getPrinter()
                        == null
                        ? null
                        : printJob.getPrinter()
                        .getName(),

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

                printJob.getLastErrorMessage()
        );
    }
}