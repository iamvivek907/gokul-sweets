package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchActiveRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchCreateRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchResponse;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchUpdateRequest;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Coordinates admin branch operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBranchService {

    private final BranchRepository branchRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /*
     * =========================================================
     * LIST ADMIN BRANCHES
     * =========================================================
     */
    /**
     * Returns branches.
     *
     * @return the get branches result
     */
    @Transactional(readOnly = true)
    public List<AdminBranchResponse> getBranches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "getBranches()");
        try {
            requireBranchManagePermission();
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            List<Branch> branches;
            if (isOwnerAdmin(staff)) {
                branches = branchRepository.findAll();
            } else {
                /*
                 * Non-owner administrators may only see branches
                 * explicitly assigned through staff_branch_access.
                 *
                 * We copy the lazy collection while the transaction
                 * is open and sort it deterministically.
                 */
                branches = staff.getBranches().stream().toList();
            }
            return branches.stream()
                    .sorted(
                            Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER)
                                    .thenComparing(Branch::getId))
                    .map(AdminBranchResponse::from)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchService.class, "getBranches()");
        }
    }

    /*
     * =========================================================
     * GET ADMIN BRANCH
     * =========================================================
     */
    /**
     * Returns branch.
     *
     * @param branchId the branch id
     * @return the get branch result
     */
    @Transactional(readOnly = true)
    public AdminBranchResponse getBranch(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "getBranch(Long)");
        try {
            requireBranchManagePermission();
            Branch branch = getBranchEntity(branchId);
            staffAuthorizationService.requireBranchAccess(branch.getId());
            return AdminBranchResponse.from(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchService.class, "getBranch(Long)");
        }
    }

    /*
     * =========================================================
     * CREATE BRANCH
     * =========================================================
     *
     * Creating a brand-new branch is owner-only.
     *
     * A normal branch manager cannot be granted access to a
     * branch that does not exist yet, so creation belongs to
     * OWNER_ADMIN.
     */
    /**
     * Creates branch.
     *
     * @param request the request
     * @return the create branch result
     */
    @Transactional
    public AdminBranchResponse createBranch(AdminBranchCreateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class, "createBranch(AdminBranchCreateRequest)");
        try {
            requireBranchManagePermission();
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            requireOwnerAdmin(staff);
            String code = normalizeCode(request.code());
            branchRepository
                    .findByCode(code)
                    .ifPresent(
                            existing -> {
                                throw new IllegalArgumentException(
                                        "A branch with code " + code + " already exists.");
                            });
            validateCoordinates(request.latitude(), request.longitude());
            validateOpeningHours(request.openingTime(), request.closingTime());
            Branch branch = new Branch();
            branch.setCode(code);
            applyEditableFields(
                    branch,
                    request.name(),
                    request.address(),
                    request.city(),
                    request.state(),
                    request.pincode(),
                    request.phone(),
                    request.fssaiLicenceNumber(),
                    request.latitude(),
                    request.longitude(),
                    request.openingTime(),
                    request.closingTime());
            branch.setActive(request.active() == null || request.active());
            Branch saved = branchRepository.saveAndFlush(branch);
            log.info(
                    "Branch created: branchId={}, code={}, createdByStaffUserId={}",
                    saved.getId(),
                    saved.getCode(),
                    staff.getId());
            return AdminBranchResponse.from(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "createBranch(AdminBranchCreateRequest)");
        }
    }

    /*
     * =========================================================
     * UPDATE BRANCH
     * =========================================================
     */
    /**
     * Updates branch.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update branch result
     */
    @Transactional
    public AdminBranchResponse updateBranch(Long branchId, AdminBranchUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class, "updateBranch(Long,AdminBranchUpdateRequest)");
        try {
            requireBranchManagePermission();
            Branch branch = getBranchEntity(branchId);
            staffAuthorizationService.requireBranchAccess(branch.getId());
            validateCoordinates(request.latitude(), request.longitude());
            validateOpeningHours(request.openingTime(), request.closingTime());
            applyEditableFields(
                    branch,
                    request.name(),
                    request.address(),
                    request.city(),
                    request.state(),
                    request.pincode(),
                    request.phone(),
                    request.fssaiLicenceNumber(),
                    request.latitude(),
                    request.longitude(),
                    request.openingTime(),
                    request.closingTime());
            Branch saved = branchRepository.saveAndFlush(branch);
            log.info(
                    "Branch updated: branchId={}, code={}, updatedByStaffUserId={}",
                    saved.getId(),
                    saved.getCode(),
                    staffAuthorizationService.getCurrentStaff().getId());
            return AdminBranchResponse.from(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "updateBranch(Long,AdminBranchUpdateRequest)");
        }
    }

    /*
     * =========================================================
     * ACTIVATE / DEACTIVATE
     * =========================================================
     */
    /**
     * Updates active status.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update active status result
     */
    @Transactional
    public AdminBranchResponse updateActiveStatus(Long branchId, AdminBranchActiveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class,
                        "updateActiveStatus(Long,AdminBranchActiveRequest)");
        try {
            requireBranchManagePermission();
            Branch branch = getBranchEntity(branchId);
            staffAuthorizationService.requireBranchAccess(branch.getId());
            branch.setActive(request.active());
            Branch saved = branchRepository.saveAndFlush(branch);
            log.info(
                    "Branch active status changed: branchId={}, code={}, active={},"
                            + " updatedByStaffUserId={}",
                    saved.getId(),
                    saved.getCode(),
                    saved.isActive(),
                    staffAuthorizationService.getCurrentStaff().getId());
            return AdminBranchResponse.from(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "updateActiveStatus(Long,AdminBranchActiveRequest)");
        }
    }

    /*
     * =========================================================
     * ENTITY LOOKUP
     * =========================================================
     */
    /**
     * Returns branch entity.
     *
     * @param branchId the branch id
     * @return the get branch entity result
     */
    private Branch getBranchEntity(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "getBranchEntity(Long)");
        try {
            if (branchId == null) {
                throw new IllegalArgumentException("Branch ID is required.");
            }
            return branchRepository
                    .findById(branchId)
                    .orElseThrow(
                            () -> {
                                log.warn("Admin branch not found: branchId={}", branchId);
                                return new IllegalArgumentException("Branch does not exist.");
                            });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchService.class, "getBranchEntity(Long)");
        }
    }

    /*
     * =========================================================
     * PERMISSIONS
     * =========================================================
     */
    /** Requires branch manage permission. */
    private void requireBranchManagePermission() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "requireBranchManagePermission()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.BRANCH_MANAGE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "requireBranchManagePermission()");
        }
    }

    /**
     * Reports whether owner admin.
     *
     * @param staff the staff
     * @return the is owner admin result
     */
    private boolean isOwnerAdmin(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "isOwnerAdmin(StaffUser)");
        try {
            return staff.getRole() != null && "OWNER_ADMIN".equals(staff.getRole().getName());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchService.class, "isOwnerAdmin(StaffUser)");
        }
    }

    /**
     * Requires owner admin.
     *
     * @param staff the staff
     */
    private void requireOwnerAdmin(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "requireOwnerAdmin(StaffUser)");
        try {
            if (!isOwnerAdmin(staff)) {
                log.warn("Non-owner attempted to create a branch: staffUserId={}", staff.getId());
                throw new AccessDeniedException(
                        "Only the owner administrator can create a branch.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "requireOwnerAdmin(StaffUser)");
        }
    }

    /*
     * =========================================================
     * EDITABLE FIELDS
     * =========================================================
     */
    /**
     * Apply editable fields.
     *
     * @param branch the branch
     * @param name the name
     * @param address the address
     * @param city the city
     * @param state the state
     * @param pincode the pincode
     * @param phone the phone
     * @param fssaiLicenceNumber the fssai licence number
     * @param latitude the latitude
     * @param longitude the longitude
     * @param openingTime the opening time
     * @param closingTime the closing time
     */
    private void applyEditableFields(
            Branch branch,
            String name,
            String address,
            String city,
            String state,
            String pincode,
            String phone,
            String fssaiLicenceNumber,
            BigDecimal latitude,
            BigDecimal longitude,
            java.time.LocalTime openingTime,
            java.time.LocalTime closingTime) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class,
                        "applyEditableFields(Branch,String,String,String,String,String,String,String,BigDecimal,BigDecimal,java.time.LocalTime,java.time.LocalTime)");
        try {
            branch.setName(normalizeRequiredText(name, "Branch name"));
            branch.setAddress(normalizeNullableText(address));
            branch.setCity(normalizeNullableText(city));
            branch.setState(normalizeNullableText(state));
            branch.setPincode(normalizeNullableText(pincode));
            branch.setPhone(normalizeNullableText(phone));
            branch.setFssaiLicenceNumber(normalizeNullableText(fssaiLicenceNumber));
            branch.setLatitude(latitude);
            branch.setLongitude(longitude);
            branch.setOpeningTime(openingTime);
            branch.setClosingTime(closingTime);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "applyEditableFields(Branch,String,String,String,String,String,String,String,BigDecimal,BigDecimal,java.time.LocalTime,java.time.LocalTime)");
        }
    }

    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */
    /**
     * Validates coordinates.
     *
     * @param latitude the latitude
     * @param longitude the longitude
     */
    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class, "validateCoordinates(BigDecimal,BigDecimal)");
        try {
            if ((latitude == null) != (longitude == null)) {
                throw new IllegalArgumentException("Latitude and longitude must both be provided.");
            }
            if (latitude != null
                    && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                            || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90.");
            }
            if (longitude != null
                    && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                            || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "validateCoordinates(BigDecimal,BigDecimal)");
        }
    }

    /**
     * Validates opening hours.
     *
     * @param openingTime the opening time
     * @param closingTime the closing time
     */
    private void validateOpeningHours(
            java.time.LocalTime openingTime, java.time.LocalTime closingTime) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class,
                        "validateOpeningHours(java.time.LocalTime,java.time.LocalTime)");
        try {
            if ((openingTime == null) != (closingTime == null)) {
                throw new IllegalArgumentException(
                        "Opening and closing time must both be provided.");
            }
            if (openingTime == null) {
                return;
            }
            if (openingTime.equals(closingTime)) {
                throw new IllegalArgumentException("Opening and closing time cannot be the same.");
            }
            /*
             * Closing time may be earlier than opening time for
             * branches that operate across midnight.
             *
             * Example: 18:00 to 02:00.
             */
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "validateOpeningHours(java.time.LocalTime,java.time.LocalTime)");
        }
    }

    /*
     * =========================================================
     * NORMALIZATION
     * =========================================================
     */
    /**
     * Normalizes code.
     *
     * @param value the value
     * @return the normalize code result
     */
    private String normalizeCode(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "normalizeCode(String)");
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Branch code is required.");
            }
            return value.trim().toUpperCase();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchService.class, "normalizeCode(String)");
        }
    }

    /**
     * Normalizes required text.
     *
     * @param value the value
     * @param fieldName the field name
     * @return the normalize required text result
     */
    private String normalizeRequiredText(String value, String fieldName) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchService.class, "normalizeRequiredText(String,String)");
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "normalizeRequiredText(String,String)");
        }
    }

    /**
     * Normalizes nullable text.
     *
     * @param value the value
     * @return the normalize nullable text result
     */
    private String normalizeNullableText(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchService.class, "normalizeNullableText(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchService.class,
                    "normalizeNullableText(String)");
        }
    }
}
