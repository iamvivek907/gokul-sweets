package com.gokulsweets.restaurant.kot.controller;

import com.gokulsweets.restaurant.kot.dto.AdminKotReprintResponse;
import com.gokulsweets.restaurant.kot.service.KotReprintService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin kot reprint operations. */
@RestController
@RequestMapping("/api/admin/kot")
@RequiredArgsConstructor
@Slf4j
public class AdminKotReprintController {

    private final KotReprintService kotReprintService;

    /*
     * =========================================================
     * QUEUE AUTOMATIC KOT REPRINT
     * =========================================================
     */
    /**
     * Reprints kot.
     *
     * @param kotNumber the kot number
     * @return the reprint kot result
     */
    @PostMapping("/{kotNumber}/reprint")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<AdminKotReprintResponse> reprintKot(@PathVariable String kotNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminKotReprintController.class, "reprintKot(String)");
        try {
            log.debug("Admin automatic KOT reprint requested: kotNumber={}", kotNumber);
            return ResponseEntity.ok(kotReprintService.queueReprint(kotNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminKotReprintController.class,
                    "reprintKot(String)");
        }
    }
}
