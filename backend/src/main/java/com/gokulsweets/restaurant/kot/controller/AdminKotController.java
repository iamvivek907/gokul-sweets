package com.gokulsweets.restaurant.kot.controller;

import com.gokulsweets.restaurant.kot.dto.AdminKotResponse;
import com.gokulsweets.restaurant.kot.service.KotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kot")
@RequiredArgsConstructor
@Slf4j
public class AdminKotController {

    private final KotService kotService;


    /*
     * =========================================================
     * GET KOT BY KOT NUMBER
     * =========================================================
     */

    @GetMapping("/{kotNumber}")
    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    public ResponseEntity<AdminKotResponse>
    getKotByNumber(
            @PathVariable
            String kotNumber
    ) {

        log.debug(
                "Admin KOT requested: kotNumber={}",
                kotNumber
        );


        return ResponseEntity.ok(
                kotService
                        .getAdminKotByNumber(
                                kotNumber
                        )
        );
    }


    /*
     * =========================================================
     * GET KOT BY ORDER NUMBER
     * =========================================================
     */

    @GetMapping("/order/{orderNumber}")
    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    public ResponseEntity<AdminKotResponse>
    getKotByOrderNumber(
            @PathVariable
            String orderNumber
    ) {

        log.debug(
                "Admin KOT requested by order number: orderNumber={}",
                orderNumber
        );


        return ResponseEntity.ok(
                kotService
                        .getAdminKotByOrderNumber(
                                orderNumber
                        )
        );
    }


    /*
     * =========================================================
     * RECORD PRINT ATTEMPT
     * =========================================================
     *
     * Stage-1 browser printing records that a staff member
     * initiated printing through the application.
     */

    @PostMapping("/{kotNumber}/print")
    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    public ResponseEntity<AdminKotResponse>
    recordPrintAttempt(
            @PathVariable
            String kotNumber
    ) {

        log.debug(
                "Admin KOT print requested: kotNumber={}",
                kotNumber
        );


        return ResponseEntity.ok(
                kotService
                        .recordPrintAttempt(
                                kotNumber
                        )
        );
    }
}