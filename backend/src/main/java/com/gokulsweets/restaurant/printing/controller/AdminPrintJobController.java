package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobCountsResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobPageResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobResponse;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.service.AdminPrintJobService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin print job operations. */
@RestController
@RequestMapping("/api/admin/print-jobs")
@RequiredArgsConstructor
public class AdminPrintJobController {

    private final AdminPrintJobService printJobService;

    /**
     * Handles {@code GET /api/admin/print-jobs} for admin print job.
     *
     * @param branchId the branch id supplied to this method
     * @param status the status supplied to this method
     * @param page the page supplied to this method
     * @param size the size supplied to this method
     * @return the value of {@code ResponseEntity.ok(printJobService.getJobs(branchId, status, page,
     *     size))}
     */
    @GetMapping
    public ResponseEntity<AdminPrintJobPageResponse> list(
            @RequestParam Long branchId,
            @RequestParam(required = false) PrintJobStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintJobController.class, "list(Long,PrintJobStatus,Integer,Integer)");
        try {
            return ResponseEntity.ok(printJobService.getJobs(branchId, status, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintJobController.class,
                    "list(Long,PrintJobStatus,Integer,Integer)");
        }
    }

    /**
     * Handles {@code GET /api/admin/print-jobs/counts} for admin print job.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code ResponseEntity.ok(printJobService.getCounts(branchId))}
     */
    @GetMapping("/counts")
    public ResponseEntity<AdminPrintJobCountsResponse> counts(@RequestParam Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobController.class, "counts(Long)");
        try {
            return ResponseEntity.ok(printJobService.getCounts(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrintJobController.class, "counts(Long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/print-jobs/{printJobId}/retry} for admin print job.
     *
     * <p>Delegates to {@code printJobService.retry(...)}.
     *
     * @param printJobId the print job id supplied to this method
     * @return the value of {@code ResponseEntity.ok(printJobService.retry(printJobId))}
     */
    @PostMapping("/{printJobId}/retry")
    public ResponseEntity<AdminPrintJobResponse> retry(@PathVariable Long printJobId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrintJobController.class, "retry(Long)");
        try {
            return ResponseEntity.ok(printJobService.retry(printJobId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrintJobController.class, "retry(Long)");
        }
    }
}
