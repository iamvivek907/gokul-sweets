package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentFailedRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentPrintedRequest;
import com.gokulsweets.restaurant.printing.service.PrintAgentAuthenticationService;
import com.gokulsweets.restaurant.printing.service.PrintAgentService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for print agent operations. */
@RestController
@RequestMapping("/api/print-agent")
@RequiredArgsConstructor
public class PrintAgentController {

    private static final String API_KEY_HEADER = AppConstant.PRINT_AGENT_CONTROLLER_API_KEY_HEADER;

    private final PrintAgentAuthenticationService authenticationService;

    private final PrintAgentService printAgentService;

    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */
    /**
     * Heartbeats the operation.
     *
     * @param apiKey the api key
     * @param request the request
     * @return the heartbeat result
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<PrintAgentHeartbeatResponse> heartbeat(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody PrintAgentHeartbeatRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentController.class, "heartbeat(String,PrintAgentHeartbeatRequest)");
        try {
            authenticationService.authenticate(apiKey);
            return ResponseEntity.ok(printAgentService.heartbeat(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentController.class,
                    "heartbeat(String,PrintAgentHeartbeatRequest)");
        }
    }

    /*
     * =========================================================
     * CLAIM NEXT JOB
     * =========================================================
     */
    /**
     * Claims the operation.
     *
     * @param apiKey the api key
     * @param request the request
     * @return the claim result
     */
    @PostMapping("/jobs/claim")
    public ResponseEntity<PrintAgentClaimResponse> claim(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody PrintAgentClaimRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentController.class, "claim(String,PrintAgentClaimRequest)");
        try {
            authenticationService.authenticate(apiKey);
            return printAgentService
                    .claimNext(request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentController.class,
                    "claim(String,PrintAgentClaimRequest)");
        }
    }

    /*
     * =========================================================
     * PRINTED
     * =========================================================
     */
    /**
     * Printeds the operation.
     *
     * @param apiKey the api key
     * @param printJobId the print job id
     * @param request the request
     * @return the printed result
     */
    @PostMapping("/jobs/{printJobId}/printed")
    public ResponseEntity<Void> printed(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long printJobId,
            @Valid @RequestBody PrintAgentPrintedRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentController.class,
                        "printed(String,Long,PrintAgentPrintedRequest)");
        try {
            authenticationService.authenticate(apiKey);
            printAgentService.markPrinted(printJobId, request);
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentController.class,
                    "printed(String,Long,PrintAgentPrintedRequest)");
        }
    }

    /*
     * =========================================================
     * FAILED
     * =========================================================
     */
    /**
     * Faileds the operation.
     *
     * @param apiKey the api key
     * @param printJobId the print job id
     * @param request the request
     * @return the failed result
     */
    @PostMapping("/jobs/{printJobId}/failed")
    public ResponseEntity<Void> failed(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long printJobId,
            @Valid @RequestBody PrintAgentFailedRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentController.class, "failed(String,Long,PrintAgentFailedRequest)");
        try {
            authenticationService.authenticate(apiKey);
            printAgentService.markFailed(printJobId, request);
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentController.class,
                    "failed(String,Long,PrintAgentFailedRequest)");
        }
    }
}
