package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HTTP endpoints for pickup slot generator operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/pickup-slots")
@RequiredArgsConstructor
public class PickupSlotGeneratorController {

    private final PickupSlotGeneratorService pickupSlotGeneratorService;

    /**
     * Generates slots.
     *
     * @param branchId the branch id
     * @param date the date
     * @return the generate slots result
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public List<PickupSlotResponse> generateSlots(
            @PathVariable Long branchId, @RequestParam LocalDate date) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotGeneratorController.class, "generateSlots(Long,LocalDate)");
        try {
            return pickupSlotGeneratorService.generateSlots(branchId, date);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotGeneratorController.class,
                    "generateSlots(Long,LocalDate)");
        }
    }
}
