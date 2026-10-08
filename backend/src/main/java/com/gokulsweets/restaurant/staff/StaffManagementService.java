package com.gokulsweets.restaurant.staff;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.dto.CreateStaffOpeningBalanceRequest;
import com.gokulsweets.restaurant.staff.dto.CreateStaffPayrollSetupRequest;
import com.gokulsweets.restaurant.staff.dto.CreateStaffRequest;
import com.gokulsweets.restaurant.staff.dto.ResetStaffPasswordRequest;
import com.gokulsweets.restaurant.staff.dto.StaffBranchOptionResponse;
import com.gokulsweets.restaurant.staff.dto.StaffManagementOptionsResponse;
import com.gokulsweets.restaurant.staff.dto.StaffResponse;
import com.gokulsweets.restaurant.staff.dto.StaffRoleOptionResponse;
import com.gokulsweets.restaurant.staff.dto.UpdateStaffRequest;
import com.gokulsweets.restaurant.staff.payroll.StaffCompensationProfile;
import com.gokulsweets.restaurant.staff.payroll.StaffCompensationProfileRepository;
import com.gokulsweets.restaurant.staff.payroll.StaffPayrollOpeningBalance;
import com.gokulsweets.restaurant.staff.payroll.StaffPayrollOpeningBalanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates staff management operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffManagementService {

    private static final String OWNER_ROLE = AppConstant.STAFF_MANAGEMENT_SERVICE_OWNER_ROLE;

    private final StaffUserRepository staffUserRepository;

    private final RoleRepository roleRepository;

    private final BranchRepository branchRepository;

    private final PasswordEncoder passwordEncoder;

    private final StaffAuthorizationService staffAuthorizationService;

    private final StaffCompensationProfileRepository compensationRepository;

    private final StaffPayrollOpeningBalanceRepository openingBalanceRepository;

    /**
     * Creates staff.
     *
     * @param request the request
     * @return the create staff result
     */
    @Transactional
    public StaffResponse createStaff(CreateStaffRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "createStaff(CreateStaffRequest)");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            String username = request.username().trim().toLowerCase();
            if (staffUserRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("Username already exists.");
            }
            Role role = getRole(request.roleName());
            requireAssignableRole(actor, role);
            Set<Branch> branches = resolveBranches(actor, role, request.branchIds());
            StaffUser staff = new StaffUser();
            staff.setUsername(username);
            staff.setPasswordHash(passwordEncoder.encode(request.password()));
            staff.setFullName(request.fullName().trim());
            staff.setPhone(normalizeNullable(request.phone()));
            staff.setActive(true);
            staff.setRole(role);
            staff.setBranches(branches);
            StaffUser saved = staffUserRepository.save(staff);
            if (request.payroll() != null) {
                createInitialPayrollSetup(actor, saved, request.payroll());
            }
            log.info(
                    "Staff user created: staffUserId={}, username={}, role={},"
                            + " createdByStaffUserId={}",
                    saved.getId(),
                    saved.getUsername(),
                    role.getName(),
                    actor.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "createStaff(CreateStaffRequest)");
        }
    }

    /**
     * Returns all staff.
     *
     * @return the get all staff result
     */
    @Transactional(readOnly = true)
    public List<StaffResponse> getAllStaff() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "getAllStaff()");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            List<StaffUser> allStaff = staffUserRepository.findAllWithRoleAndBranches();
            if (isOwner(actor)) {
                return allStaff.stream().map(this::toResponse).toList();
            }
            Set<Long> actorBranchIds = branchIdsOf(actor);
            return allStaff.stream()
                    .filter(staff -> !isOwner(staff))
                    .filter(staff -> isFullyWithinBranchScope(staff, actorBranchIds))
                    .map(this::toResponse)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "getAllStaff()");
        }
    }

    /**
     * Returns staff.
     *
     * @param staffId the staff id
     * @return the get staff result
     */
    @Transactional(readOnly = true)
    public StaffResponse getStaff(Long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "getStaff(Long)");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            StaffUser staff = getDetailedStaff(staffId);
            requireTargetManageable(actor, staff);
            return toResponse(staff);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "getStaff(Long)");
        }
    }

    /**
     * Updates staff.
     *
     * @param staffId the staff id
     * @param request the request
     * @return the update staff result
     */
    @Transactional
    public StaffResponse updateStaff(Long staffId, UpdateStaffRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class, "updateStaff(Long,UpdateStaffRequest)");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            StaffUser staff = getDetailedStaff(staffId);
            requireTargetManageable(actor, staff);
            Role newRole = getRole(request.roleName());
            requireAssignableRole(actor, newRole);
            if (staff.getId().equals(actor.getId())) {
                if (!staff.getRole().getName().equals(newRole.getName())
                        || !branchIdsOf(staff).equals(normalizeBranchIds(request.branchIds()))
                        || staff.isActive() != request.active()) {
                    throw new IllegalStateException(
                            "You cannot change your own role, branch access or active status.");
                }
            }
            boolean currentlyActiveOwner = staff.isActive() && isOwner(staff);
            boolean remainsActiveOwner = request.active() && OWNER_ROLE.equals(newRole.getName());
            if (currentlyActiveOwner && !remainsActiveOwner) {
                long activeOwnerCount =
                        staffUserRepository.countByRoleNameAndActiveTrue(OWNER_ROLE);
                if (activeOwnerCount <= 1) {
                    throw new IllegalStateException(
                            "The last active owner account cannot be deactivated or assigned"
                                    + " another role.");
                }
            }
            Set<Branch> branches = resolveBranches(actor, newRole, request.branchIds());
            staff.setFullName(request.fullName().trim());
            staff.setPhone(normalizeNullable(request.phone()));
            staff.setRole(newRole);
            staff.setBranches(branches);
            staff.setActive(request.active());
            StaffUser saved = staffUserRepository.save(staff);
            log.info(
                    "Staff user updated: staffUserId={}, role={}, active={},"
                            + " updatedByStaffUserId={}",
                    saved.getId(),
                    saved.getRole().getName(),
                    saved.isActive(),
                    actor.getId());
            return toResponse(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "updateStaff(Long,UpdateStaffRequest)");
        }
    }

    /**
     * Resets password.
     *
     * @param staffId the staff id
     * @param request the request
     */
    @Transactional
    public void resetPassword(Long staffId, ResetStaffPasswordRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class,
                        "resetPassword(Long,ResetStaffPasswordRequest)");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            StaffUser staff = getDetailedStaff(staffId);
            requireTargetManageable(actor, staff);
            staff.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            staffUserRepository.save(staff);
            log.info(
                    "Staff password reset: staffUserId={}, resetByStaffUserId={}",
                    staffId,
                    actor.getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "resetPassword(Long,ResetStaffPasswordRequest)");
        }
    }

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @Transactional(readOnly = true)
    public StaffManagementOptionsResponse getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "getOptions()");
        try {
            requireStaffManage();
            StaffUser actor = staffAuthorizationService.getCurrentStaff();
            List<StaffRoleOptionResponse> roles =
                    roleRepository.findAll().stream()
                            .filter(role -> canAssignRole(actor, role))
                            .sorted(Comparator.comparing(Role::getName))
                            .map(this::toRoleOption)
                            .toList();
            List<Branch> branches =
                    isOwner(actor)
                            ? branchRepository.findAll()
                            : actor.getBranches().stream().toList();
            List<StaffBranchOptionResponse> branchOptions =
                    branches.stream()
                            .sorted(
                                    Comparator.comparing(
                                                    Branch::getName, String.CASE_INSENSITIVE_ORDER)
                                            .thenComparing(Branch::getId))
                            .map(
                                    branch ->
                                            new StaffBranchOptionResponse(
                                                    branch.getId(),
                                                    branch.getCode(),
                                                    branch.getName(),
                                                    branch.isActive()))
                            .toList();
            return new StaffManagementOptionsResponse(roles, branchOptions);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "getOptions()");
        }
    }

    /**
     * Creates initial payroll setup.
     *
     * @param actor the actor
     * @param staff the staff
     * @param payroll the payroll
     */
    private void createInitialPayrollSetup(
            StaffUser actor, StaffUser staff, CreateStaffPayrollSetupRequest payroll) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class,
                        "createInitialPayrollSetup(StaffUser,StaffUser,CreateStaffPayrollSetupRequest)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.PAYROLL_MANAGE);
            if (payroll.effectiveFrom() == null) {
                throw new IllegalArgumentException("Payroll effective date is required.");
            }
            BigDecimal dailyRate = normalizeMoney(payroll.dailyRate(), "Daily rate");
            BigDecimal halfDayRate = normalizeMoney(payroll.halfDayRate(), "Half-day rate");
            StaffCompensationProfile compensation = new StaffCompensationProfile();
            compensation.setStaffUser(staff);
            compensation.setEffectiveFrom(payroll.effectiveFrom());
            compensation.setDailyRate(dailyRate);
            compensation.setHalfDayRate(halfDayRate);
            compensation.setCreatedByStaffUser(actor);
            compensationRepository.save(compensation);
            if (payroll.openingBalance() != null) {
                createOpeningBalance(actor, staff, payroll.openingBalance());
            }
            log.info(
                    "Initial payroll configured during staff creation: staffUserId={},"
                            + " effectiveFrom={}, createdByStaffUserId={}",
                    staff.getId(),
                    payroll.effectiveFrom(),
                    actor.getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "createInitialPayrollSetup(StaffUser,StaffUser,CreateStaffPayrollSetupRequest)");
        }
    }

    /**
     * Creates opening balance.
     *
     * @param actor the actor
     * @param staff the staff
     * @param opening the opening
     */
    private void createOpeningBalance(
            StaffUser actor, StaffUser staff, CreateStaffOpeningBalanceRequest opening) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class,
                        "createOpeningBalance(StaffUser,StaffUser,CreateStaffOpeningBalanceRequest)");
        try {
            if (opening.asOfDate() == null) {
                throw new IllegalArgumentException("Opening balance date is required.");
            }
            BigDecimal earnedAmount =
                    normalizeMoney(opening.earnedAmount(), "Previously earned amount");
            BigDecimal takenAmount =
                    normalizeMoney(opening.takenAmount(), "Previously taken amount");
            if (takenAmount.compareTo(earnedAmount) > 0) {
                throw new IllegalArgumentException(
                        "Previously taken amount cannot be greater than previously earned amount.");
            }
            StaffPayrollOpeningBalance balance = new StaffPayrollOpeningBalance();
            balance.setStaffUser(staff);
            balance.setAsOfDate(opening.asOfDate());
            balance.setEarnedAmount(earnedAmount);
            balance.setTakenAmount(takenAmount);
            balance.setNote(normalizeNullable(opening.note()));
            balance.setCreatedByStaffUser(actor);
            openingBalanceRepository.save(balance);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "createOpeningBalance(StaffUser,StaffUser,CreateStaffOpeningBalanceRequest)");
        }
    }

    /**
     * Normalizes money.
     *
     * @param value the value
     * @param fieldName the field name
     * @return the normalize money result
     */
    private BigDecimal normalizeMoney(BigDecimal value, String fieldName) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class, "normalizeMoney(BigDecimal,String)");
        try {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            if (value.signum() < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative.");
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "normalizeMoney(BigDecimal,String)");
        }
    }

    /** Requires staff manage. */
    private void requireStaffManage() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "requireStaffManage()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.STAFF_MANAGE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "requireStaffManage()");
        }
    }

    /**
     * Requires target manageable.
     *
     * @param actor the actor
     * @param target the target
     */
    private void requireTargetManageable(StaffUser actor, StaffUser target) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class,
                        "requireTargetManageable(StaffUser,StaffUser)");
        try {
            if (isOwner(actor)) {
                return;
            }
            if (isOwner(target)) {
                log.warn(
                        "Non-owner attempted to manage owner account: requesterId={},"
                                + " targetStaffId={}",
                        actor.getId(),
                        target.getId());
                throw new AccessDeniedException("Only an owner can manage an owner account.");
            }
            Set<Long> actorBranchIds = branchIdsOf(actor);
            if (!isFullyWithinBranchScope(target, actorBranchIds)) {
                log.warn(
                        "Staff management denied outside branch scope: requesterId={},"
                                + " targetStaffId={}",
                        actor.getId(),
                        target.getId());
                throw new AccessDeniedException(
                        "You cannot manage this staff member because their branch access is outside"
                                + " your scope.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "requireTargetManageable(StaffUser,StaffUser)");
        }
    }

    /**
     * Reports whether fully within branch scope.
     *
     * @param target the target
     * @param actorBranchIds the actor branch ids
     * @return the is fully within branch scope result
     */
    private boolean isFullyWithinBranchScope(StaffUser target, Set<Long> actorBranchIds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class,
                        "isFullyWithinBranchScope(StaffUser,Set<Long>)");
        try {
            Set<Long> targetBranchIds = branchIdsOf(target);
            return !targetBranchIds.isEmpty() && actorBranchIds.containsAll(targetBranchIds);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "isFullyWithinBranchScope(StaffUser,Set<Long>)");
        }
    }

    /**
     * Requires assignable role.
     *
     * @param actor the actor
     * @param role the role
     */
    private void requireAssignableRole(StaffUser actor, Role role) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class, "requireAssignableRole(StaffUser,Role)");
        try {
            if (!canAssignRole(actor, role)) {
                log.warn(
                        "Staff role assignment denied: requesterId={}, requestedRole={}",
                        actor.getId(),
                        role.getName());
                throw new AccessDeniedException("You cannot assign this role.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "requireAssignableRole(StaffUser,Role)");
        }
    }

    /**
     * Reports whether assign role.
     *
     * @param actor the actor
     * @param role the role
     * @return the can assign role result
     */
    private boolean canAssignRole(StaffUser actor, Role role) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "canAssignRole(StaffUser,Role)");
        try {
            if (isOwner(actor)) {
                return true;
            }
            if (OWNER_ROLE.equals(role.getName())) {
                return false;
            }
            Set<PermissionName> actorPermissions =
                    actor.getRole().getPermissions().stream()
                            .map(Permission::getName)
                            .collect(Collectors.toSet());
            Set<PermissionName> rolePermissions =
                    role.getPermissions().stream()
                            .map(Permission::getName)
                            .collect(Collectors.toSet());
            return actorPermissions.containsAll(rolePermissions);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "canAssignRole(StaffUser,Role)");
        }
    }

    /**
     * Resolves branches.
     *
     * @param actor the actor
     * @param role the role
     * @param branchIds the branch ids
     * @return the resolve branches result
     */
    private Set<Branch> resolveBranches(StaffUser actor, Role role, Set<Long> branchIds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementService.class, "resolveBranches(StaffUser,Role,Set<Long>)");
        try {
            if (OWNER_ROLE.equals(role.getName())) {
                if (!isOwner(actor)) {
                    throw new AccessDeniedException("Only an owner can assign the owner role.");
                }
                return new HashSet<>();
            }
            Set<Long> normalizedBranchIds = normalizeBranchIds(branchIds);
            if (normalizedBranchIds.isEmpty()) {
                throw new IllegalArgumentException("At least one branch must be assigned.");
            }
            if (!isOwner(actor)) {
                Set<Long> actorBranchIds = branchIdsOf(actor);
                if (!actorBranchIds.containsAll(normalizedBranchIds)) {
                    throw new AccessDeniedException(
                            "You cannot assign staff to a branch outside your access.");
                }
            }
            List<Branch> branches = branchRepository.findAllById(normalizedBranchIds);
            if (branches.size() != normalizedBranchIds.size()) {
                throw new IllegalArgumentException("One or more branches do not exist.");
            }
            return new HashSet<>(branches);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "resolveBranches(StaffUser,Role,Set<Long>)");
        }
    }

    /**
     * Returns detailed staff.
     *
     * @param staffId the staff id
     * @return the get detailed staff result
     */
    private StaffUser getDetailedStaff(Long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "getDetailedStaff(Long)");
        try {
            if (staffId == null) {
                throw new IllegalArgumentException("Staff ID is required.");
            }
            return staffUserRepository
                    .findDetailedById(staffId)
                    .orElseThrow(() -> new IllegalArgumentException("Staff user does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "getDetailedStaff(Long)");
        }
    }

    /**
     * Returns role.
     *
     * @param roleName the role name
     * @return the get role result
     */
    private Role getRole(String roleName) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "getRole(String)");
        try {
            String normalizedRole = roleName.trim().toUpperCase();
            return roleRepository
                    .findByName(normalizedRole)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid staff role."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "getRole(String)");
        }
    }

    /**
     * Reports whether owner.
     *
     * @param staff the staff
     * @return the is owner result
     */
    private boolean isOwner(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "isOwner(StaffUser)");
        try {
            return staff.getRole() != null && OWNER_ROLE.equals(staff.getRole().getName());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "isOwner(StaffUser)");
        }
    }

    /**
     * Branches ids of.
     *
     * @param staff the staff
     * @return the branch ids of result
     */
    private Set<Long> branchIdsOf(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "branchIdsOf(StaffUser)");
        try {
            return staff.getBranches().stream().map(Branch::getId).collect(Collectors.toSet());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "branchIdsOf(StaffUser)");
        }
    }

    /**
     * Normalizes branch ids.
     *
     * @param branchIds the branch ids
     * @return the normalize branch ids result
     */
    private Set<Long> normalizeBranchIds(Set<Long> branchIds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "normalizeBranchIds(Set<Long>)");
        try {
            if (branchIds == null) {
                return Set.of();
            }
            return branchIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "normalizeBranchIds(Set<Long>)");
        }
    }

    /**
     * Tos role option.
     *
     * @param role the role
     * @return the to role option result
     */
    private StaffRoleOptionResponse toRoleOption(Role role) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "toRoleOption(Role)");
        try {
            Set<String> permissions =
                    role.getPermissions().stream()
                            .map(permission -> permission.getName().name())
                            .collect(Collectors.toCollection(java.util.TreeSet::new));
            return new StaffRoleOptionResponse(role.getName(), role.getDescription(), permissions);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementService.class, "toRoleOption(Role)");
        }
    }

    /**
     * Tos response.
     *
     * @param staff the staff
     * @return the to response result
     */
    private StaffResponse toResponse(StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "toResponse(StaffUser)");
        try {
            Set<Long> branchIds = branchIdsOf(staff);
            return new StaffResponse(
                    staff.getId(),
                    staff.getUsername(),
                    staff.getFullName(),
                    staff.getPhone(),
                    staff.isActive(),
                    staff.getRole().getName(),
                    branchIds);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "toResponse(StaffUser)");
        }
    }

    /**
     * Normalizes nullable.
     *
     * @param value the value
     * @return the normalize nullable result
     */
    private String normalizeNullable(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementService.class, "normalizeNullable(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementService.class,
                    "normalizeNullable(String)");
        }
    }
}
