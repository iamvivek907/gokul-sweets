package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.rebate.dto.CreateRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.RebateResponse;
import com.gokulsweets.restaurant.rebate.dto.RebateSlabRequest;
import com.gokulsweets.restaurant.rebate.dto.RebateSlabResponse;
import com.gokulsweets.restaurant.rebate.dto.UpdateRebateRequest;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates rebate management operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RebateManagementService {

    private final RebateRepository rebateRepository;

    private final RebateSlabRepository rebateSlabRepository;

    private final RebateCustomerRepository rebateCustomerRepository;

    private final BranchRepository branchRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    // =========================================================
    // CREATE
    // =========================================================
    /**
     * Creates the operation.
     *
     * @param request the request
     * @return the create result
     */
    @Transactional
    public RebateResponse create(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "create(CreateRebateRequest)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REBATE_MANAGE);
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            validateRequest(request);
            String code = normalizeCode(request.code());
            if (rebateRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("Rebate code already exists.");
            }
            Branch branch = resolveBranch(request.branchId(), currentStaff);
            Rebate rebate = new Rebate();
            rebate.setCode(code);
            rebate.setName(request.name().trim());
            rebate.setDescription(normalizeNullable(request.description()));
            rebate.setScope(request.scope());
            rebate.setVisibility(request.visibility());
            rebate.setRebateType(request.rebateType());
            if (request.rebateType() == RebateType.SLAB) {
                rebate.setRebateValue(null);
                rebate.setMinimumOrderAmount(null);
                rebate.setMaximumDiscountAmount(null);
            } else {
                rebate.setRebateValue(money(request.rebateValue()));
                rebate.setMinimumOrderAmount(moneyNullable(request.minimumOrderAmount()));
                rebate.setMaximumDiscountAmount(moneyNullable(request.maximumDiscountAmount()));
            }
            rebate.setMaxTotalUses(request.maxTotalUses());
            rebate.setMaxUsesPerCustomer(request.maxUsesPerCustomer());
            rebate.setBranch(branch);
            rebate.setValidFrom(request.validFrom());
            rebate.setValidUntil(request.validUntil());
            rebate.setActive(true);
            rebate.setCreatedBy(currentStaff);
            Rebate saved = rebateRepository.save(rebate);
            List<RebateSlab> savedSlabs = saveSlabs(saved, request);
            List<RebateCustomer> savedCustomers = saveCustomers(saved, request);
            log.info(
                    "Rebate created: rebateId={}, code={}, scope={}, visibility={}, type={},"
                            + " branchId={}, createdByStaffId={}",
                    saved.getId(),
                    saved.getCode(),
                    saved.getScope(),
                    saved.getVisibility(),
                    saved.getRebateType(),
                    saved.getBranch() == null ? null : saved.getBranch().getId(),
                    currentStaff.getId());
            return toResponse(saved, savedSlabs, savedCustomers);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "create(CreateRebateRequest)");
        }
    }

    // =========================================================
    // GET ONE
    // =========================================================
    /**
     * Returns by id.
     *
     * @param rebateId the rebate id
     * @return the get by id result
     */
    @Transactional(readOnly = true)
    public RebateResponse getById(Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "getById(Long)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REBATE_VIEW);
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            Rebate rebate = getRebate(rebateId);
            requireViewAccess(rebate, currentStaff);
            return buildResponse(rebate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "getById(Long)");
        }
    }

    // =========================================================
    // GET ALL
    // =========================================================
    /**
     * Returns all.
     *
     * @return the get all result
     */
    @Transactional(readOnly = true)
    public List<RebateResponse> getAll() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "getAll()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REBATE_VIEW);
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            List<Rebate> rebates = rebateRepository.findAllByOrderByCreatedAtDesc();
            return rebates.stream()
                    .filter(rebate -> canView(rebate, currentStaff))
                    .map(this::buildResponse)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "getAll()");
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================
    /**
     * Updates the operation.
     *
     * @param rebateId the rebate id
     * @param request the request
     * @return the update result
     */
    @Transactional
    public RebateResponse update(Long rebateId, UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "update(Long,UpdateRebateRequest)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REBATE_MANAGE);
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            Rebate rebate = getRebate(rebateId);
            /*
             * Validate access against the existing rebate first.
             *
             * This prevents a manager from taking a rebate
             * belonging to another branch and moving it.
             */
            requireManageAccess(rebate, currentStaff);
            validateUpdateRequest(request);
            String code = normalizeCode(request.code());
            if (rebateRepository.existsByCodeIgnoreCaseAndIdNot(code, rebateId)) {
                throw new IllegalArgumentException("Rebate code already exists.");
            }
            Branch newBranch = resolveBranch(request.branchId(), currentStaff);
            rebate.setCode(code);
            rebate.setName(request.name().trim());
            rebate.setDescription(normalizeNullable(request.description()));
            rebate.setScope(request.scope());
            rebate.setVisibility(request.visibility());
            rebate.setRebateType(request.rebateType());
            if (request.rebateType() == RebateType.SLAB) {
                rebate.setRebateValue(null);
                rebate.setMinimumOrderAmount(null);
                rebate.setMaximumDiscountAmount(null);
            } else {
                rebate.setRebateValue(money(request.rebateValue()));
                rebate.setMinimumOrderAmount(moneyNullable(request.minimumOrderAmount()));
                rebate.setMaximumDiscountAmount(moneyNullable(request.maximumDiscountAmount()));
            }
            rebate.setMaxTotalUses(request.maxTotalUses());
            rebate.setMaxUsesPerCustomer(request.maxUsesPerCustomer());
            rebate.setBranch(newBranch);
            rebate.setValidFrom(request.validFrom());
            rebate.setValidUntil(request.validUntil());
            rebateRepository.save(rebate);
            /*
             * Replace existing child configuration.
             */
            rebateSlabRepository.deleteByRebateId(rebateId);
            rebateCustomerRepository.deleteByRebateId(rebateId);
            /*
             * Force Hibernate to execute the DELETEs
             * before inserting replacement rows.
             */
            rebateSlabRepository.flush();
            rebateCustomerRepository.flush();
            if (request.rebateType() == RebateType.SLAB) {
                saveUpdatedSlabs(rebate, request.slabs());
            }
            if (request.scope() == RebateScope.CUSTOMER) {
                saveUpdatedCustomers(rebate, request.customerPhones());
            }
            log.info(
                    "Rebate updated: rebateId={}, code={}, visibility={}, staffUserId={}",
                    rebate.getId(),
                    rebate.getCode(),
                    rebate.getVisibility(),
                    currentStaff.getId());
            return buildResponse(rebate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "update(Long,UpdateRebateRequest)");
        }
    }

    // =========================================================
    // ACTIVATE / DEACTIVATE
    // =========================================================
    /**
     * Activates the operation.
     *
     * @param rebateId the rebate id
     * @return the activate result
     */
    @Transactional
    public RebateResponse activate(Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "activate(Long)");
        try {
            return changeActiveStatus(rebateId, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "activate(Long)");
        }
    }

    /**
     * Deactivates the operation.
     *
     * @param rebateId the rebate id
     * @return the deactivate result
     */
    @Transactional
    public RebateResponse deactivate(Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "deactivate(Long)");
        try {
            return changeActiveStatus(rebateId, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "deactivate(Long)");
        }
    }

    /**
     * Changes active status.
     *
     * @param rebateId the rebate id
     * @param active the active
     * @return the change active status result
     */
    private RebateResponse changeActiveStatus(Long rebateId, boolean active) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "changeActiveStatus(Long,boolean)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.REBATE_MANAGE);
            StaffUser currentStaff = staffAuthorizationService.getCurrentStaff();
            Rebate rebate = getRebate(rebateId);
            requireManageAccess(rebate, currentStaff);
            if (rebate.isActive() == active) {
                return buildResponse(rebate);
            }
            rebate.setActive(active);
            rebateRepository.save(rebate);
            log.info(
                    "Rebate active status changed: rebateId={}, active={}, staffUserId={}",
                    rebateId,
                    active,
                    currentStaff.getId());
            return buildResponse(rebate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "changeActiveStatus(Long,boolean)");
        }
    }

    // =========================================================
    // CREATE VALIDATION
    // =========================================================
    /**
     * Validates request.
     *
     * @param request the request
     */
    private void validateRequest(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateRequest(CreateRebateRequest)");
        try {
            if (!request.validUntil().isAfter(request.validFrom())) {
                throw new IllegalArgumentException("Rebate end time must be after start time.");
            }
            validateUsageLimits(request);
            validateScope(request);
            switch (request.rebateType()) {
                case PERCENTAGE -> validatePercentage(request);
                case FIXED_AMOUNT -> validateFixedAmount(request);
                case SLAB -> validateSlab(request);
                default -> throw new IllegalArgumentException("Unsupported rebate type.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateRequest(CreateRebateRequest)");
        }
    }

    /**
     * Validates usage limits.
     *
     * @param request the request
     */
    private void validateUsageLimits(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateUsageLimits(CreateRebateRequest)");
        try {
            if (request.maxTotalUses() != null
                    && request.maxUsesPerCustomer() != null
                    && request.maxUsesPerCustomer() > request.maxTotalUses()) {
                throw new IllegalArgumentException(
                        "Maximum uses per customer cannot exceed total rebate usage limit.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateUsageLimits(CreateRebateRequest)");
        }
    }

    /**
     * Validates scope.
     *
     * @param request the request
     */
    private void validateScope(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateScope(CreateRebateRequest)");
        try {
            if (request.scope() == RebateScope.CUSTOMER) {
                if (request.customerPhones() == null || request.customerPhones().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Customer-specific rebate requires at least one customer phone.");
                }
                request.customerPhones().forEach(this::normalizePhone);
                return;
            }
            if (request.customerPhones() != null && !request.customerPhones().isEmpty()) {
                throw new IllegalArgumentException(
                        "General rebate cannot contain customer assignments.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateScope(CreateRebateRequest)");
        }
    }

    /**
     * Validates percentage.
     *
     * @param request the request
     */
    private void validatePercentage(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validatePercentage(CreateRebateRequest)");
        try {
            requirePositive(request.rebateValue(), "Percentage rebate value is required.");
            if (request.rebateValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percentage rebate cannot exceed 100%.");
            }
            requirePositive(
                    request.maximumDiscountAmount(),
                    "Maximum rebate amount is required for percentage rebates.");
            validateMinimumAmount(request.minimumOrderAmount());
            requireNoSlabs(request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validatePercentage(CreateRebateRequest)");
        }
    }

    /**
     * Validates fixed amount.
     *
     * @param request the request
     */
    private void validateFixedAmount(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateFixedAmount(CreateRebateRequest)");
        try {
            requirePositive(request.rebateValue(), "Fixed rebate amount is required.");
            validateMinimumAmount(request.minimumOrderAmount());
            if (request.maximumDiscountAmount() != null) {
                throw new IllegalArgumentException(
                        "Maximum rebate amount must not be provided for fixed amount rebates.");
            }
            requireNoSlabs(request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateFixedAmount(CreateRebateRequest)");
        }
    }

    /**
     * Validates slab.
     *
     * @param request the request
     */
    private void validateSlab(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateSlab(CreateRebateRequest)");
        try {
            if (request.rebateValue() != null
                    || request.minimumOrderAmount() != null
                    || request.maximumDiscountAmount() != null) {
                throw new IllegalArgumentException(
                        "Slab rebates must use slab rules instead of rebate value, minimum order"
                                + " amount, or maximum rebate amount.");
            }
            if (request.slabs() == null || request.slabs().isEmpty()) {
                throw new IllegalArgumentException("At least one rebate slab is required.");
            }
            validateSlabRules(request.slabs());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateSlab(CreateRebateRequest)");
        }
    }

    // =========================================================
    // UPDATE VALIDATION
    // =========================================================
    /**
     * Validates update request.
     *
     * @param request the request
     */
    private void validateUpdateRequest(UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class,
                        "validateUpdateRequest(UpdateRebateRequest)");
        try {
            if (!request.validUntil().isAfter(request.validFrom())) {
                throw new IllegalArgumentException("Rebate end time must be after start time.");
            }
            if (request.maxTotalUses() != null
                    && request.maxUsesPerCustomer() != null
                    && request.maxUsesPerCustomer() > request.maxTotalUses()) {
                throw new IllegalArgumentException(
                        "Maximum uses per customer cannot exceed total usage limit.");
            }
            if (request.scope() == RebateScope.CUSTOMER) {
                if (request.customerPhones() == null || request.customerPhones().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Customer-specific rebate requires at least one customer phone.");
                }
                request.customerPhones().forEach(this::normalizePhone);
            } else if (request.customerPhones() != null && !request.customerPhones().isEmpty()) {
                throw new IllegalArgumentException(
                        "General rebate cannot contain customer assignments.");
            }
            switch (request.rebateType()) {
                case PERCENTAGE -> validatePercentageUpdate(request);
                case FIXED_AMOUNT -> validateFixedUpdate(request);
                case SLAB -> validateSlabUpdate(request);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateUpdateRequest(UpdateRebateRequest)");
        }
    }

    /**
     * Validates percentage update.
     *
     * @param request the request
     */
    private void validatePercentageUpdate(UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class,
                        "validatePercentageUpdate(UpdateRebateRequest)");
        try {
            requirePositive(request.rebateValue(), "Percentage rebate value is required.");
            if (request.rebateValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percentage rebate cannot exceed 100%.");
            }
            requirePositive(
                    request.maximumDiscountAmount(),
                    "Maximum rebate amount is required for percentage rebates.");
            validateMinimumAmount(request.minimumOrderAmount());
            if (request.slabs() != null && !request.slabs().isEmpty()) {
                throw new IllegalArgumentException("Percentage rebate cannot contain slabs.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validatePercentageUpdate(UpdateRebateRequest)");
        }
    }

    /**
     * Validates fixed update.
     *
     * @param request the request
     */
    private void validateFixedUpdate(UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateFixedUpdate(UpdateRebateRequest)");
        try {
            requirePositive(request.rebateValue(), "Fixed rebate amount is required.");
            validateMinimumAmount(request.minimumOrderAmount());
            if (request.maximumDiscountAmount() != null) {
                throw new IllegalArgumentException(
                        "Maximum rebate amount must not be provided for fixed amount rebates.");
            }
            if (request.slabs() != null && !request.slabs().isEmpty()) {
                throw new IllegalArgumentException("Fixed rebate cannot contain slabs.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateFixedUpdate(UpdateRebateRequest)");
        }
    }

    /**
     * Validates slab update.
     *
     * @param request the request
     */
    private void validateSlabUpdate(UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateSlabUpdate(UpdateRebateRequest)");
        try {
            if (request.rebateValue() != null
                    || request.minimumOrderAmount() != null
                    || request.maximumDiscountAmount() != null) {
                throw new IllegalArgumentException("Slab rebate must use slab rules only.");
            }
            if (request.slabs() == null || request.slabs().isEmpty()) {
                throw new IllegalArgumentException("At least one rebate slab is required.");
            }
            validateSlabRules(request.slabs());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateSlabUpdate(UpdateRebateRequest)");
        }
    }

    /**
     * Validates slab rules.
     *
     * @param slabs the slabs
     */
    private void validateSlabRules(List<RebateSlabRequest> slabs) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class,
                        "validateSlabRules(List<RebateSlabRequest>)");
        try {
            Set<BigDecimal> thresholds = new HashSet<>();
            for (RebateSlabRequest slab : slabs) {
                BigDecimal minimum = money(slab.minimumOrderAmount());
                BigDecimal rebateAmount = money(slab.rebateAmount());
                if (minimum.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Slab minimum order amount must be greater than zero.");
                }
                if (rebateAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Slab rebate amount must be greater than zero.");
                }
                if (rebateAmount.compareTo(minimum) >= 0) {
                    throw new IllegalArgumentException(
                            "Slab rebate amount must be less than its minimum order amount.");
                }
                if (!thresholds.add(minimum)) {
                    throw new IllegalArgumentException(
                            "Duplicate slab minimum order amount is not allowed.");
                }
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateSlabRules(List<RebateSlabRequest>)");
        }
    }

    // =========================================================
    // AUTHORIZATION
    // =========================================================
    /**
     * Requires view access.
     *
     * @param rebate the rebate
     * @param staff the staff
     */
    private void requireViewAccess(Rebate rebate, StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "requireViewAccess(Rebate,StaffUser)");
        try {
            if ("OWNER_ADMIN".equals(staff.getRole().getName())) {
                return;
            }
            /*
             * Global rebates are visible to managers
             * because they apply across all branches.
             */
            if (rebate.getBranch() == null) {
                return;
            }
            staffAuthorizationService.requireBranchAccess(rebate.getBranch().getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "requireViewAccess(Rebate,StaffUser)");
        }
    }

    /**
     * Requires manage access.
     *
     * @param rebate the rebate
     * @param staff the staff
     */
    private void requireManageAccess(Rebate rebate, StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "requireManageAccess(Rebate,StaffUser)");
        try {
            if ("OWNER_ADMIN".equals(staff.getRole().getName())) {
                return;
            }
            /*
             * Global rebates can only be managed
             * by OWNER_ADMIN.
             */
            if (rebate.getBranch() == null) {
                throw new AccessDeniedException("Only an owner can manage a global rebate.");
            }
            staffAuthorizationService.requireBranchAccess(rebate.getBranch().getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "requireManageAccess(Rebate,StaffUser)");
        }
    }

    /**
     * Reports whether view.
     *
     * @param rebate the rebate
     * @param staff the staff
     * @return the can view result
     */
    private boolean canView(Rebate rebate, StaffUser staff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "canView(Rebate,StaffUser)");
        try {
            if ("OWNER_ADMIN".equals(staff.getRole().getName())) {
                return true;
            }
            if (rebate.getBranch() == null) {
                return true;
            }
            return staff.getBranches().stream()
                    .anyMatch(branch -> branch.getId().equals(rebate.getBranch().getId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "canView(Rebate,StaffUser)");
        }
    }

    /**
     * Resolves branch.
     *
     * @param branchId the branch id
     * @param currentStaff the current staff
     * @return the resolve branch result
     */
    private Branch resolveBranch(Long branchId, StaffUser currentStaff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "resolveBranch(Long,StaffUser)");
        try {
            /*
             * null branch = rebate valid for all branches.
             */
            if (branchId == null) {
                if (!"OWNER_ADMIN".equals(currentStaff.getRole().getName())) {
                    throw new AccessDeniedException(
                            "Only an owner can manage rebates for all branches.");
                }
                return null;
            }
            staffAuthorizationService.requireBranchAccess(branchId);
            return branchRepository
                    .findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "resolveBranch(Long,StaffUser)");
        }
    }

    // =========================================================
    // CHILD ENTITY SAVING
    // =========================================================
    /**
     * Saves slabs.
     *
     * @param rebate the rebate
     * @param request the request
     * @return the save slabs result
     */
    private List<RebateSlab> saveSlabs(Rebate rebate, CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "saveSlabs(Rebate,CreateRebateRequest)");
        try {
            if (request.rebateType() != RebateType.SLAB) {
                return List.of();
            }
            List<RebateSlab> slabs =
                    request.slabs().stream()
                            .sorted(Comparator.comparing(RebateSlabRequest::minimumOrderAmount))
                            .map(
                                    slabRequest -> {
                                        RebateSlab slab = new RebateSlab();
                                        slab.setRebate(rebate);
                                        slab.setMinimumOrderAmount(
                                                money(slabRequest.minimumOrderAmount()));
                                        slab.setRebateAmount(money(slabRequest.rebateAmount()));
                                        return slab;
                                    })
                            .toList();
            return rebateSlabRepository.saveAll(slabs);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "saveSlabs(Rebate,CreateRebateRequest)");
        }
    }

    /**
     * Saves customers.
     *
     * @param rebate the rebate
     * @param request the request
     * @return the save customers result
     */
    private List<RebateCustomer> saveCustomers(Rebate rebate, CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "saveCustomers(Rebate,CreateRebateRequest)");
        try {
            if (request.scope() != RebateScope.CUSTOMER) {
                return List.of();
            }
            Set<String> normalizedPhones =
                    request.customerPhones().stream()
                            .map(this::normalizePhone)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            List<RebateCustomer> customers = new ArrayList<>();
            for (String phone : normalizedPhones) {
                RebateCustomer customer = new RebateCustomer();
                customer.setRebate(rebate);
                customer.setCustomerPhone(phone);
                customers.add(customer);
            }
            return rebateCustomerRepository.saveAll(customers);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "saveCustomers(Rebate,CreateRebateRequest)");
        }
    }

    /**
     * Saves updated slabs.
     *
     * @param rebate the rebate
     * @param requests the requests
     */
    private void saveUpdatedSlabs(Rebate rebate, List<RebateSlabRequest> requests) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class,
                        "saveUpdatedSlabs(Rebate,List<RebateSlabRequest>)");
        try {
            List<RebateSlab> slabs =
                    requests.stream()
                            .sorted(Comparator.comparing(RebateSlabRequest::minimumOrderAmount))
                            .map(
                                    request -> {
                                        RebateSlab slab = new RebateSlab();
                                        slab.setRebate(rebate);
                                        slab.setMinimumOrderAmount(
                                                money(request.minimumOrderAmount()));
                                        slab.setRebateAmount(money(request.rebateAmount()));
                                        return slab;
                                    })
                            .toList();
            rebateSlabRepository.saveAll(slabs);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "saveUpdatedSlabs(Rebate,List<RebateSlabRequest>)");
        }
    }

    /**
     * Saves updated customers.
     *
     * @param rebate the rebate
     * @param phones the phones
     */
    private void saveUpdatedCustomers(Rebate rebate, Set<String> phones) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "saveUpdatedCustomers(Rebate,Set<String>)");
        try {
            Set<String> normalized =
                    phones.stream()
                            .map(this::normalizePhone)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            List<RebateCustomer> customers =
                    normalized.stream()
                            .map(
                                    phone -> {
                                        RebateCustomer customer = new RebateCustomer();
                                        customer.setRebate(rebate);
                                        customer.setCustomerPhone(phone);
                                        return customer;
                                    })
                            .toList();
            rebateCustomerRepository.saveAll(customers);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "saveUpdatedCustomers(Rebate,Set<String>)");
        }
    }

    // =========================================================
    // RESPONSE BUILDING
    // =========================================================
    /**
     * Builds response.
     *
     * @param rebate the rebate
     * @return the build response result
     */
    private RebateResponse buildResponse(Rebate rebate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "buildResponse(Rebate)");
        try {
            List<RebateSlab> slabs =
                    rebateSlabRepository.findByRebateIdOrderByMinimumOrderAmountAsc(rebate.getId());
            List<RebateCustomer> customers =
                    rebateCustomerRepository.findByRebateId(rebate.getId());
            return toResponse(rebate, slabs, customers);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "buildResponse(Rebate)");
        }
    }

    /**
     * Tos response.
     *
     * @param rebate the rebate
     * @param slabs the slabs
     * @param customers the customers
     * @return the to response result
     */
    private RebateResponse toResponse(
            Rebate rebate, List<RebateSlab> slabs, List<RebateCustomer> customers) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class,
                        "toResponse(Rebate,List<RebateSlab>,List<RebateCustomer>)");
        try {
            List<RebateSlabResponse> slabResponses =
                    slabs.stream()
                            .map(
                                    slab ->
                                            new RebateSlabResponse(
                                                    slab.getMinimumOrderAmount(),
                                                    slab.getRebateAmount()))
                            .toList();
            Set<String> phones =
                    customers.stream()
                            .map(RebateCustomer::getCustomerPhone)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            return new RebateResponse(
                    rebate.getId(),
                    rebate.getCode(),
                    rebate.getName(),
                    rebate.getDescription(),
                    rebate.getScope(),
                    rebate.getVisibility(),
                    rebate.getRebateType(),
                    rebate.getRebateValue(),
                    rebate.getMinimumOrderAmount(),
                    rebate.getMaximumDiscountAmount(),
                    rebate.getMaxTotalUses(),
                    rebate.getMaxUsesPerCustomer(),
                    rebate.getBranch() == null ? null : rebate.getBranch().getId(),
                    rebate.getBranch() == null ? null : rebate.getBranch().getName(),
                    rebate.getValidFrom(),
                    rebate.getValidUntil(),
                    rebate.isActive(),
                    slabResponses,
                    phones,
                    rebate.getCreatedBy().getFullName(),
                    rebate.getCreatedAt(),
                    rebate.getUpdatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "toResponse(Rebate,List<RebateSlab>,List<RebateCustomer>)");
        }
    }

    // =========================================================
    // DATABASE HELPERS
    // =========================================================
    /**
     * Returns rebate.
     *
     * @param rebateId the rebate id
     * @return the get rebate result
     */
    private Rebate getRebate(Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "getRebate(Long)");
        try {
            return rebateRepository
                    .findById(rebateId)
                    .orElseThrow(() -> new IllegalArgumentException("Rebate does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "getRebate(Long)");
        }
    }

    // =========================================================
    // GENERAL VALIDATION HELPERS
    // =========================================================
    /**
     * Requires no slabs.
     *
     * @param request the request
     */
    private void requireNoSlabs(CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "requireNoSlabs(CreateRebateRequest)");
        try {
            if (request.slabs() != null && !request.slabs().isEmpty()) {
                throw new IllegalArgumentException("Slabs are only allowed for SLAB rebates.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "requireNoSlabs(CreateRebateRequest)");
        }
    }

    /**
     * Requires positive.
     *
     * @param value the value
     * @param missingMessage the missing message
     */
    private void requirePositive(BigDecimal value, String missingMessage) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "requirePositive(BigDecimal,String)");
        try {
            if (value == null) {
                throw new IllegalArgumentException(missingMessage);
            }
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Rebate amount must be greater than zero.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "requirePositive(BigDecimal,String)");
        }
    }

    /**
     * Validates minimum amount.
     *
     * @param amount the amount
     */
    private void validateMinimumAmount(BigDecimal amount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementService.class, "validateMinimumAmount(BigDecimal)");
        try {
            if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Minimum order amount cannot be negative.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "validateMinimumAmount(BigDecimal)");
        }
    }

    // =========================================================
    // NORMALIZATION
    // =========================================================
    /**
     * Money the operation.
     *
     * @param value the value
     * @return the money result
     */
    private BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "money(BigDecimal)");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementService.class, "money(BigDecimal)");
        }
    }

    /**
     * Money nullable.
     *
     * @param value the value
     * @return the money nullable result
     */
    private BigDecimal moneyNullable(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "moneyNullable(BigDecimal)");
        try {
            return value == null ? null : money(value);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "moneyNullable(BigDecimal)");
        }
    }

    /**
     * Normalizes code.
     *
     * @param code the code
     * @return the normalize code result
     */
    private String normalizeCode(String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "normalizeCode(String)");
        try {
            return code.trim().toUpperCase();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "normalizeCode(String)");
        }
    }

    /**
     * Normalizes phone.
     *
     * @param phone the phone
     * @return the normalize phone result
     */
    private String normalizePhone(String phone) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementService.class, "normalizePhone(String)");
        try {
            if (phone == null) {
                throw new IllegalArgumentException("Customer phone is required.");
            }
            String normalized = phone.trim().replaceAll("\\s+", "");
            if (!normalized.matches("\\d{10}")) {
                throw new IllegalArgumentException(
                        "Customer phone must contain exactly 10 digits.");
            }
            return normalized;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "normalizePhone(String)");
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
                MethodTiming.start(RebateManagementService.class, "normalizeNullable(String)");
        try {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementService.class,
                    "normalizeNullable(String)");
        }
    }
}
