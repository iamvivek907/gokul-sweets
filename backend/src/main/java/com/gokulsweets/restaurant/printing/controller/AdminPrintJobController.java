package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobCountsResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobPageResponse;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobResponse;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.service.AdminPrintJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/print-jobs")
@RequiredArgsConstructor
public class AdminPrintJobController {

    private final AdminPrintJobService
            printJobService;


    @GetMapping
    public ResponseEntity<AdminPrintJobPageResponse> list(

            @RequestParam
            Long branchId,

            @RequestParam(
                    required = false
            )
            PrintJobStatus status,

            @RequestParam(
                    defaultValue = "0"
            )
            Integer page,

            @RequestParam(
                    defaultValue = "20"
            )
            Integer size
    ) {

        return ResponseEntity.ok(
                printJobService
                        .getJobs(
                                branchId,
                                status,
                                page,
                                size
                        )
        );
    }


    @GetMapping("/counts")
    public ResponseEntity<AdminPrintJobCountsResponse> counts(

            @RequestParam
            Long branchId
    ) {

        return ResponseEntity.ok(
                printJobService
                        .getCounts(
                                branchId
                        )
        );
    }


    @PostMapping(
            "/{printJobId}/retry"
    )
    public ResponseEntity<AdminPrintJobResponse> retry(

            @PathVariable
            Long printJobId
    ) {

        return ResponseEntity.ok(
                printJobService
                        .retry(
                                printJobId
                        )
        );
    }
}