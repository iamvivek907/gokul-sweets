package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.dto.CreatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdatePickupSlotRequest;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HTTP endpoints for pickup slot operations. */
@RestController
@RequestMapping
public class PickupSlotController {

    private final PickupSlotService pickupSlotService;

    private final StaffAuthorizationService authorization;

    private final PickupSlotRepository repository;

    /**
     * Creates a pickup slot controller instance.
     *
     * @param pickupSlotService the pickup slot service
     * @param authorization the authorization
     * @param repository the repository
     */
    public PickupSlotController(
            PickupSlotService pickupSlotService,
            StaffAuthorizationService authorization,
            PickupSlotRepository repository) {
        this.pickupSlotService = pickupSlotService;
        this.authorization = authorization;
        this.repository = repository;
    }

    /**
     * Lists slots.
     *
     * @param branchId the branch id
     * @param startDate the start date
     * @param endDate the end date
     * @return the list slots result
     */
    @GetMapping("/api/admin/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> listSlots(
            @PathVariable Long branchId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotController.class, "listSlots(Long,LocalDate,LocalDate)");
        try {
            authorize(branchId);
            if (endDate.isBefore(startDate) || endDate.isAfter(startDate.plusDays(60))) {
                throw new IllegalArgumentException("Choose a date range of at most 61 days.");
            }
            return repository
                    .findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                            branchId, startDate, endDate)
                    .stream()
                    .map(PickupSlotResponse::from)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotController.class,
                    "listSlots(Long,LocalDate,LocalDate)");
        }
    }

    /**
     * Checks authorization for pickup slot data.
     *
     * <p>Authorization checks include {@code PermissionName.BRANCH_MANAGE}.
     *
     * @param branchId the branch id supplied to this method
     */
    private void authorize(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupSlotController.class, "authorize(Long)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupSlotController.class, "authorize(Long)");
        }
    }

    /**
     * Authorizes slot.
     *
     * @param slotId the slot id
     */
    private void authorizeSlot(Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupSlotController.class, "authorizeSlot(Long)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            PickupSlot slot =
                    repository
                            .findWithBranchById(slotId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Pickup slot not found."));
            authorization.requireBranchAccess(slot.getBranch().getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupSlotController.class, "authorizeSlot(Long)");
        }
    }

    /**
     * Returns available slots.
     *
     * @param branchId the branch id
     * @param date the date
     * @return the get available slots result
     */
    @GetMapping("/api/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> getAvailableSlots(
            @PathVariable Long branchId, @RequestParam LocalDate date) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupSlotController.class, "getAvailableSlots(Long,LocalDate)");
        try {
            return pickupSlotService.getAvailableSlots(branchId, date);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotController.class,
                    "getAvailableSlots(Long,LocalDate)");
        }
    }

    /**
     * Creates slots.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the create slots result
     */
    @PostMapping("/api/admin/branches/{branchId}/pickup-slots")
    public List<PickupSlotResponse> createSlots(
            @PathVariable Long branchId, @Valid @RequestBody CreatePickupSlotRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotController.class, "createSlots(Long,CreatePickupSlotRequest)");
        try {
            authorize(branchId);
            return pickupSlotService.createSlots(branchId, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotController.class,
                    "createSlots(Long,CreatePickupSlotRequest)");
        }
    }

    /**
     * Updates slot.
     *
     * @param slotId the slot id
     * @param request the request
     * @return the update slot result
     */
    @PutMapping("/api/admin/pickup-slots/{slotId}")
    public PickupSlotResponse updateSlot(
            @PathVariable Long slotId, @Valid @RequestBody UpdatePickupSlotRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotController.class, "updateSlot(Long,UpdatePickupSlotRequest)");
        try {
            authorizeSlot(slotId);
            return pickupSlotService.updateSlot(slotId, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotController.class,
                    "updateSlot(Long,UpdatePickupSlotRequest)");
        }
    }

    /**
     * Disables slot.
     *
     * @param slotId the slot id
     */
    @DeleteMapping("/api/admin/pickup-slots/{slotId}")
    public void disableSlot(@PathVariable Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupSlotController.class, "disableSlot(Long)");
        try {
            authorizeSlot(slotId);
            pickupSlotService.disableSlot(slotId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupSlotController.class, "disableSlot(Long)");
        }
    }
}
