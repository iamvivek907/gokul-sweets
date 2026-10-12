package com.gokulsweets.restaurant.kot.controller;

import com.gokulsweets.restaurant.kot.dto.AdminKotResponse;
import com.gokulsweets.restaurant.kot.service.KotService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin kot operations. */
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
    /**
     * Returns kot by number.
     *
     * @param kotNumber the kot number
     * @return the get kot by number result
     */
    @GetMapping("/{kotNumber}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<AdminKotResponse> getKotByNumber(@PathVariable String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminKotController.class, "getKotByNumber(String)");
        try {
            log.debug("Admin KOT requested: kotNumber={}", kotNumber);
            return ResponseEntity.ok(kotService.getAdminKotByNumber(kotNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminKotController.class, "getKotByNumber(String)");
        }
    }

    /*
     * =========================================================
     * GET KOT BY ORDER NUMBER
     * =========================================================
     */
    /**
     * Returns kot by order number.
     *
     * @param orderNumber the order number
     * @return the get kot by order number result
     */
    @GetMapping("/order/{orderNumber}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<AdminKotResponse> getKotByOrderNumber(@PathVariable String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminKotController.class, "getKotByOrderNumber(String)");
        try {
            log.debug("Admin KOT requested by order number: orderNumber={}", orderNumber);
            return ResponseEntity.ok(kotService.getAdminKotByOrderNumber(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminKotController.class,
                    "getKotByOrderNumber(String)");
        }
    }

    /*
     * =========================================================
     * RECORD PRINT ATTEMPT
     * =========================================================
     *
     * Stage-1 browser printing records that a staff member
     * initiated printing through the application.
     */
    /**
     * Records print attempt.
     *
     * @param kotNumber the kot number
     * @return the record print attempt result
     */
    @PostMapping("/{kotNumber}/print")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<AdminKotResponse> recordPrintAttempt(@PathVariable String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminKotController.class, "recordPrintAttempt(String)");
        try {
            log.debug("Admin KOT print requested: kotNumber={}", kotNumber);
            return ResponseEntity.ok(kotService.recordPrintAttempt(kotNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminKotController.class,
                    "recordPrintAttempt(String)");
        }
    }
}
