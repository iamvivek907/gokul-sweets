package com.gokulsweets.restaurant.kot.controller;

import com.gokulsweets.restaurant.kot.dto.AdminKotReprintResponse;
import com.gokulsweets.restaurant.kot.service.KotReprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kot")
@RequiredArgsConstructor
@Slf4j
public class AdminKotReprintController {

    private final KotReprintService
            kotReprintService;


    /*
     * =========================================================
     * QUEUE AUTOMATIC KOT REPRINT
     * =========================================================
     */

    @PostMapping(
            "/{kotNumber}/reprint"
    )
    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    public ResponseEntity<AdminKotReprintResponse>
    reprintKot(
            @PathVariable
            String kotNumber
    ) {

        log.debug(
                "Admin automatic KOT reprint requested: kotNumber={}",
                kotNumber
        );


        return ResponseEntity.ok(
                kotReprintService
                        .queueReprint(
                                kotNumber
                        )
        );
    }
}