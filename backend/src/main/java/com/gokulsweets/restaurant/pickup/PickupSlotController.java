package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.pickup.dto.CreatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdatePickupSlotRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping
public class PickupSlotController {

    private final PickupSlotService pickupSlotService;

    public PickupSlotController(
            PickupSlotService pickupSlotService
    ) {
        this.pickupSlotService = pickupSlotService;
    }

    /*
     * Customer API
     */
    @GetMapping("/api/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> getAvailableSlots(
            @PathVariable Long branchId,
            @RequestParam LocalDate date
    ) {
        return pickupSlotService.getAvailableSlots(
                branchId,
                date
        );
    }

    /*
     * Admin API - Create
     */
    @PostMapping("/api/admin/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> createSlots(
            @PathVariable Long branchId,
            @Valid @RequestBody CreatePickupSlotRequest request
    ) {

        return pickupSlotService.createSlots(
                branchId,
                request
        );
    }

    /*
     * Admin API - Update
     */
    @PutMapping("/api/admin/pickup-slots/{slotId}")
    public PickupSlotResponse updateSlot(
            @PathVariable Long slotId,
            @Valid @RequestBody UpdatePickupSlotRequest request
    ) {
        return pickupSlotService.updateSlot(
                slotId,
                request
        );
    }

    /*
     * Admin API - Disable
     */
    @DeleteMapping("/api/admin/pickup-slots/{slotId}")
    public void disableSlot(
            @PathVariable Long slotId
    ) {
        pickupSlotService.disableSlot(slotId);
    }
}