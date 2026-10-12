package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.dto.BranchPickupSettingsResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdateBranchPickupSettingsRequest;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Coordinates branch pickup settings operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchPickupSettingsService {

    private final BranchPickupSettingsRepository settingsRepository;

    private final BranchRepository branchRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /*
     * =========================================================
     * GET SETTINGS
     * =========================================================
     */
    /**
     * Returns settings.
     *
     * @param branchId the branch id
     * @return the get settings result
     */
    @Transactional(readOnly = true)
    public BranchPickupSettingsResponse getSettings(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchPickupSettingsService.class, "getSettings(Long)");
        try {
            requireAccess(branchId);
            log.debug("Fetching pickup settings: branchId={}", branchId);
            BranchPickupSettings settings =
                    settingsRepository
                            .findByBranchId(branchId)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Pickup settings not found: branchId={}", branchId);
                                        return new IllegalArgumentException(
                                                "Pickup settings not found for branch: "
                                                        + branchId);
                                    });
            return BranchPickupSettingsResponse.from(settings);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsService.class,
                    "getSettings(Long)");
        }
    }

    /*
     * =========================================================
     * UPDATE SETTINGS
     * =========================================================
     */
    /**
     * Updates settings.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update settings result
     */
    @Transactional
    public BranchPickupSettingsResponse updateSettings(
            Long branchId, UpdateBranchPickupSettingsRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchPickupSettingsService.class,
                        "updateSettings(Long,UpdateBranchPickupSettingsRequest)");
        try {
            requireAccess(branchId);
            validateRequest(request);
            Branch branch =
                    branchRepository
                            .findById(branchId)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Cannot update pickup settings. Branch not found:"
                                                        + " branchId={}",
                                                branchId);
                                        return new IllegalArgumentException(
                                                "Branch not found: " + branchId);
                                    });
            BranchPickupSettings settings =
                    settingsRepository
                            .findByBranchId(branchId)
                            .orElseGet(
                                    () -> {
                                        log.info("Creating pickup settings: branchId={}", branchId);
                                        BranchPickupSettings created = new BranchPickupSettings();
                                        created.setBranch(branch);
                                        return created;
                                    });
            settings.setSlotDurationMinutes(request.slotDurationMinutes());
            settings.setDefaultCapacity(request.defaultCapacity());
            settings.setAdvanceBookingDays(request.advanceBookingDays());
            settings.setEnabled(request.enabled());
            settings.setOpeningTime(request.openingTime());
            settings.setClosingTime(request.closingTime());
            settings.setPriorityEnabled(request.priorityEnabled());
            if (Boolean.TRUE.equals(request.priorityEnabled())) {
                settings.setDefaultPriorityCapacity(request.defaultPriorityCapacity());
                settings.setDefaultPriorityCharge(request.defaultPriorityCharge());
            } else {
                /*
                 * Disabled priority pickup has no effective
                 * priority capacity or charge.
                 */
                settings.setDefaultPriorityCapacity(0);
                settings.setDefaultPriorityCharge(BigDecimal.ZERO);
            }
            BranchPickupSettings saved = settingsRepository.save(settings);
            log.info(
                    "Pickup settings updated: branchId={}, enabled={}, priorityEnabled={},"
                            + " updatedByStaffUserId={}",
                    branchId,
                    saved.isEnabled(),
                    saved.isPriorityEnabled(),
                    staffAuthorizationService.getCurrentStaff().getId());
            return BranchPickupSettingsResponse.from(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsService.class,
                    "updateSettings(Long,UpdateBranchPickupSettingsRequest)");
        }
    }

    /*
     * =========================================================
     * SECURITY
     * =========================================================
     */
    /**
     * Requires access.
     *
     * @param branchId the branch id
     */
    private void requireAccess(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchPickupSettingsService.class, "requireAccess(Long)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.BRANCH_MANAGE);
            staffAuthorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsService.class,
                    "requireAccess(Long)");
        }
    }

    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */
    /**
     * Validates request.
     *
     * @param request the request
     */
    private void validateRequest(UpdateBranchPickupSettingsRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchPickupSettingsService.class,
                        "validateRequest(UpdateBranchPickupSettingsRequest)");
        try {
            if (request.openingTime() == null || request.closingTime() == null) {
                throw new IllegalArgumentException(
                        "Pickup opening and closing times are required.");
            }
            if (!request.closingTime().isAfter(request.openingTime())) {
                throw new IllegalArgumentException(
                        "Pickup closing time must be after opening time.");
            }
            if (Boolean.TRUE.equals(request.priorityEnabled())
                    && request.defaultPriorityCapacity() <= 0) {
                throw new IllegalArgumentException(
                        "Priority capacity must be greater than zero when priority pickup is"
                                + " enabled.");
            }
            if (request.defaultPriorityCharge().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Priority pickup charge cannot be negative.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsService.class,
                    "validateRequest(UpdateBranchPickupSettingsRequest)");
        }
    }
}
