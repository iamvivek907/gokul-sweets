package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(
        "/api/admin/branches/{branchId}/pickup-slots"
)
@RequiredArgsConstructor
public class PickupSlotGeneratorController {

    private final PickupSlotGeneratorService
            pickupSlotGeneratorService;


    @PostMapping("/generate")
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public List<PickupSlotResponse> generateSlots(

            @PathVariable
            Long branchId,

            @RequestParam
            LocalDate date
    ) {

        return pickupSlotGeneratorService
                .generateSlots(
                        branchId,
                        date
                );
    }
}