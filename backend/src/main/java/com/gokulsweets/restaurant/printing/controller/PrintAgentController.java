package com.gokulsweets.restaurant.printing.controller;

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

@RestController
@RequestMapping("/api/print-agent")
@RequiredArgsConstructor
public class PrintAgentController {

    private static final String API_KEY_HEADER =
            "X-Print-Agent-Key";


    private final PrintAgentAuthenticationService
            authenticationService;

    private final PrintAgentService
            printAgentService;


    /*
     * =========================================================
     * HEARTBEAT
     * =========================================================
     */

    @PostMapping("/heartbeat")
    public ResponseEntity<PrintAgentHeartbeatResponse> heartbeat(

            @RequestHeader(
                    value = API_KEY_HEADER,
                    required = false
            )
            String apiKey,

            @Valid
            @RequestBody
            PrintAgentHeartbeatRequest request
    ) {

        authenticationService.authenticate(
                apiKey
        );


        return ResponseEntity.ok(
                printAgentService
                        .heartbeat(
                                request
                        )
        );
    }


    /*
     * =========================================================
     * CLAIM NEXT JOB
     * =========================================================
     */

    @PostMapping("/jobs/claim")
    public ResponseEntity<PrintAgentClaimResponse> claim(

            @RequestHeader(
                    value = API_KEY_HEADER,
                    required = false
            )
            String apiKey,

            @Valid
            @RequestBody
            PrintAgentClaimRequest request
    ) {

        authenticationService.authenticate(
                apiKey
        );


        return printAgentService
                .claimNext(
                        request
                )
                .map(
                        ResponseEntity::ok
                )
                .orElseGet(
                        () ->
                                ResponseEntity
                                        .noContent()
                                        .build()
                );
    }


    /*
     * =========================================================
     * PRINTED
     * =========================================================
     */

    @PostMapping(
            "/jobs/{printJobId}/printed"
    )
    public ResponseEntity<Void> printed(

            @RequestHeader(
                    value = API_KEY_HEADER,
                    required = false
            )
            String apiKey,

            @PathVariable
            Long printJobId,

            @Valid
            @RequestBody
            PrintAgentPrintedRequest request
    ) {

        authenticationService.authenticate(
                apiKey
        );


        printAgentService
                .markPrinted(
                        printJobId,
                        request
                );


        return ResponseEntity
                .noContent()
                .build();
    }


    /*
     * =========================================================
     * FAILED
     * =========================================================
     */

    @PostMapping(
            "/jobs/{printJobId}/failed"
    )
    public ResponseEntity<Void> failed(

            @RequestHeader(
                    value = API_KEY_HEADER,
                    required = false
            )
            String apiKey,

            @PathVariable
            Long printJobId,

            @Valid
            @RequestBody
            PrintAgentFailedRequest request
    ) {

        authenticationService.authenticate(
                apiKey
        );


        printAgentService
                .markFailed(
                        printJobId,
                        request
                );


        return ResponseEntity
                .noContent()
                .build();
    }
}