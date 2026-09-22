package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.pickup.dto.CreatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdatePickupSlotRequest;
import jakarta.validation.Valid;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping
public class PickupSlotController {

    private final PickupSlotService pickupSlotService;
    private final StaffAuthorizationService authorization;
    private final PickupSlotRepository repository;

    public PickupSlotController(
            PickupSlotService pickupSlotService,
            StaffAuthorizationService authorization,
            PickupSlotRepository repository
    ) {
        this.pickupSlotService = pickupSlotService;
        this.authorization = authorization;
        this.repository = repository;
    }

    @GetMapping("/api/admin/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> listSlots(
            @PathVariable Long branchId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate
    ) {
        authorize(branchId);
        if (endDate.isBefore(startDate) || endDate.isAfter(startDate.plusDays(60))) {
            throw new IllegalArgumentException("Choose a date range of at most 61 days.");
        }
        return repository.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(branchId, startDate, endDate)
                .stream().map(PickupSlotResponse::from).toList();
    }

    private void authorize(Long branchId) {
        authorization.requirePermission(PermissionName.BRANCH_MANAGE);
        authorization.requireBranchAccess(branchId);
    }

    private void authorizeSlot(Long slotId) {
        authorization.requirePermission(PermissionName.BRANCH_MANAGE);
        PickupSlot slot = repository.findWithBranchById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Pickup slot not found."));
        authorization.requireBranchAccess(slot.getBranch().getId());
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
        authorize(branchId);
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
        authorizeSlot(slotId);
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
        authorizeSlot(slotId);
        pickupSlotService.disableSlot(slotId);
    }
}