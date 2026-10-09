package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentFailedRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentHeartbeatResponse;
import com.gokulsweets.restaurant.printing.dto.PrintAgentJobStatusResponse;
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

    /**
     * Reads scoped job status using the private agent key; does not print, acknowledge or claim
     * work.
     *
     * @param apiKey the private print-agent credential
     * @param printJobId the job being reconciled
     * @param request the agent's branch and station identity
     * @return the current job status within that branch and station
     */
    @PostMapping("/jobs/{printJobId}/status")
    public ResponseEntity<PrintAgentJobStatusResponse> status(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long printJobId,
            @Valid @RequestBody PrintAgentClaimRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PrintAgentController.class, "status(String,Long,PrintAgentClaimRequest)");
        try {
            authenticationService.authenticate(apiKey);
            return ResponseEntity.ok(printAgentService.getJobStatus(printJobId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentController.class,
                    "status(String,Long,PrintAgentClaimRequest)");
        }
    }

    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */
    /**
     * Handles {@code POST /api/print-agent/heartbeat} for print agent.
     *
     * <p>Delegates to {@code authenticationService.authenticate(...)}, {@code
     * printAgentService.heartbeat(...)}.
     *
     * @param apiKey the api key supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.ok(printAgentService.heartbeat(request))}
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
     * Handles {@code POST /api/print-agent/jobs/claim} for print agent.
     *
     * <p>Delegates to {@code authenticationService.authenticate(...)}, {@code
     * printAgentService.claimNext(...)}.
     *
     * @param apiKey the api key supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     printAgentService.claimNext(request).map(ResponseEntity::ok).orElseGet(() ->
     *     ResponseEntity.noContent().build())}
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
     * Handles {@code POST /api/print-agent/jobs/{printJobId}/printed} for print agent.
     *
     * <p>Delegates to {@code authenticationService.authenticate(...)}, {@code
     * printAgentService.markPrinted(...)}.
     *
     * @param apiKey the api key supplied to this method
     * @param printJobId the print job id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.noContent().build()}
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
     * Handles {@code POST /api/print-agent/jobs/{printJobId}/failed} for print agent.
     *
     * <p>Delegates to {@code authenticationService.authenticate(...)}, {@code
     * printAgentService.markFailed(...)}.
     *
     * @param apiKey the api key supplied to this method
     * @param printJobId the print job id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.noContent().build()}
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
