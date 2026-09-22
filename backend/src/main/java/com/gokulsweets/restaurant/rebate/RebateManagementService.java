package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
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

    @Transactional
    public RebateResponse create(
            CreateRebateRequest request
    ) {

        staffAuthorizationService.requirePermission(
                PermissionName.REBATE_MANAGE
        );

        StaffUser currentStaff =
                staffAuthorizationService.getCurrentStaff();

        validateRequest(request);

        String code =
                normalizeCode(request.code());

        if (rebateRepository.existsByCodeIgnoreCase(code)) {

            throw new IllegalArgumentException(
                    "Rebate code already exists."
            );
        }

        Branch branch =
                resolveBranch(
                        request.branchId(),
                        currentStaff
                );

        Rebate rebate =
                new Rebate();

        rebate.setCode(code);

        rebate.setName(
                request.name().trim()
        );

        rebate.setDescription(
                normalizeNullable(
                        request.description()
                )
        );

        rebate.setScope(
                request.scope()
        );

        rebate.setVisibility(
                request.visibility()
        );

        rebate.setRebateType(
                request.rebateType()
        );

        if (request.rebateType()
                == RebateType.SLAB) {

            rebate.setRebateValue(null);
            rebate.setMinimumOrderAmount(null);
            rebate.setMaximumDiscountAmount(null);

        } else {

            rebate.setRebateValue(
                    money(
                            request.rebateValue()
                    )
            );

            rebate.setMinimumOrderAmount(
                    moneyNullable(
                            request.minimumOrderAmount()
                    )
            );

            rebate.setMaximumDiscountAmount(
                    moneyNullable(
                            request.maximumDiscountAmount()
                    )
            );
        }

        rebate.setMaxTotalUses(
                request.maxTotalUses()
        );

        rebate.setMaxUsesPerCustomer(
                request.maxUsesPerCustomer()
        );

        rebate.setBranch(branch);

        rebate.setValidFrom(
                request.validFrom()
        );

        rebate.setValidUntil(
                request.validUntil()
        );

        rebate.setActive(true);

        rebate.setCreatedBy(
                currentStaff
        );

        Rebate saved =
                rebateRepository.save(rebate);

        List<RebateSlab> savedSlabs =
                saveSlabs(
                        saved,
                        request
                );

        List<RebateCustomer> savedCustomers =
                saveCustomers(
                        saved,
                        request
                );

        log.info(
                "Rebate created: rebateId={}, code={}, scope={}, visibility={}, type={}, branchId={}, createdByStaffId={}",
                saved.getId(),
                saved.getCode(),
                saved.getScope(),
                saved.getVisibility(),
                saved.getRebateType(),
                saved.getBranch() == null
                        ? null
                        : saved.getBranch().getId(),
                currentStaff.getId()
        );

        return toResponse(
                saved,
                savedSlabs,
                savedCustomers
        );
    }

    // =========================================================
    // GET ONE
    // =========================================================

    @Transactional(readOnly = true)
    public RebateResponse getById(
            Long rebateId
    ) {

        staffAuthorizationService.requirePermission(
                PermissionName.REBATE_VIEW
        );

        StaffUser currentStaff =
                staffAuthorizationService.getCurrentStaff();

        Rebate rebate =
                getRebate(rebateId);

        requireViewAccess(
                rebate,
                currentStaff
        );

        return buildResponse(rebate);
    }

    // =========================================================
    // GET ALL
    // =========================================================

    @Transactional(readOnly = true)
    public List<RebateResponse> getAll() {

        staffAuthorizationService.requirePermission(
                PermissionName.REBATE_VIEW
        );

        StaffUser currentStaff =
                staffAuthorizationService.getCurrentStaff();

        List<Rebate> rebates =
                rebateRepository
                        .findAllByOrderByCreatedAtDesc();

        return rebates.stream()

                .filter(rebate ->
                        canView(
                                rebate,
                                currentStaff
                        )
                )

                .map(this::buildResponse)

                .toList();
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Transactional
    public RebateResponse update(
            Long rebateId,
            UpdateRebateRequest request
    ) {

        staffAuthorizationService.requirePermission(
                PermissionName.REBATE_MANAGE
        );

        StaffUser currentStaff =
                staffAuthorizationService.getCurrentStaff();

        Rebate rebate =
                getRebate(rebateId);

        /*
         * Validate access against the existing rebate first.
         *
         * This prevents a manager from taking a rebate
         * belonging to another branch and moving it.
         */
        requireManageAccess(
                rebate,
                currentStaff
        );

        validateUpdateRequest(
                request
        );

        String code =
                normalizeCode(
                        request.code()
                );

        if (rebateRepository
                .existsByCodeIgnoreCaseAndIdNot(
                        code,
                        rebateId
                )) {

            throw new IllegalArgumentException(
                    "Rebate code already exists."
            );
        }

        Branch newBranch =
                resolveBranch(
                        request.branchId(),
                        currentStaff
                );

        rebate.setCode(code);

        rebate.setName(
                request.name().trim()
        );

        rebate.setDescription(
                normalizeNullable(
                        request.description()
                )
        );

        rebate.setScope(
                request.scope()
        );

        rebate.setVisibility(
                request.visibility()
        );

        rebate.setRebateType(
                request.rebateType()
        );

        if (request.rebateType()
                == RebateType.SLAB) {

            rebate.setRebateValue(null);
            rebate.setMinimumOrderAmount(null);
            rebate.setMaximumDiscountAmount(null);

        } else {

            rebate.setRebateValue(
                    money(
                            request.rebateValue()
                    )
            );

            rebate.setMinimumOrderAmount(
                    moneyNullable(
                            request.minimumOrderAmount()
                    )
            );

            rebate.setMaximumDiscountAmount(
                    moneyNullable(
                            request.maximumDiscountAmount()
                    )
            );
        }

        rebate.setMaxTotalUses(
                request.maxTotalUses()
        );

        rebate.setMaxUsesPerCustomer(
                request.maxUsesPerCustomer()
        );

        rebate.setBranch(
                newBranch
        );

        rebate.setValidFrom(
                request.validFrom()
        );

        rebate.setValidUntil(
                request.validUntil()
        );

        rebateRepository.save(rebate);

        /*
         * Replace existing child configuration.
         */
        rebateSlabRepository.deleteByRebateId(
                rebateId
        );

        rebateCustomerRepository.deleteByRebateId(
                rebateId
        );

        /*
         * Force Hibernate to execute the DELETEs
         * before inserting replacement rows.
         */
        rebateSlabRepository.flush();
        rebateCustomerRepository.flush();


        if (request.rebateType()
                == RebateType.SLAB) {

            saveUpdatedSlabs(
                    rebate,
                    request.slabs()
            );
        }


        if (request.scope()
                == RebateScope.CUSTOMER) {

            saveUpdatedCustomers(
                    rebate,
                    request.customerPhones()
            );
        }

        log.info(
                "Rebate updated: rebateId={}, code={}, visibility={}, staffUserId={}",
                rebate.getId(),
                rebate.getCode(),
                rebate.getVisibility(),
                currentStaff.getId()
        );

        return buildResponse(rebate);
    }

    // =========================================================
    // ACTIVATE / DEACTIVATE
    // =========================================================

    @Transactional
    public RebateResponse activate(
            Long rebateId
    ) {

        return changeActiveStatus(
                rebateId,
                true
        );
    }

    @Transactional
    public RebateResponse deactivate(
            Long rebateId
    ) {

        return changeActiveStatus(
                rebateId,
                false
        );
    }

    private RebateResponse changeActiveStatus(
            Long rebateId,
            boolean active
    ) {

        staffAuthorizationService.requirePermission(
                PermissionName.REBATE_MANAGE
        );

        StaffUser currentStaff =
                staffAuthorizationService.getCurrentStaff();

        Rebate rebate =
                getRebate(rebateId);

        requireManageAccess(
                rebate,
                currentStaff
        );

        if (rebate.isActive() == active) {

            return buildResponse(
                    rebate
            );
        }

        rebate.setActive(active);

        rebateRepository.save(rebate);

        log.info(
                "Rebate active status changed: rebateId={}, active={}, staffUserId={}",
                rebateId,
                active,
                currentStaff.getId()
        );

        return buildResponse(
                rebate
        );
    }

    // =========================================================
    // CREATE VALIDATION
    // =========================================================

    private void validateRequest(
            CreateRebateRequest request
    ) {

        if (!request.validUntil()
                .isAfter(
                        request.validFrom()
                )) {

            throw new IllegalArgumentException(
                    "Rebate end time must be after start time."
            );
        }

        validateUsageLimits(request);

        validateScope(request);

        switch (request.rebateType()) {

            case PERCENTAGE ->
                    validatePercentage(request);

            case FIXED_AMOUNT ->
                    validateFixedAmount(request);

            case SLAB ->
                    validateSlab(request);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported rebate type."
                    );
        }
    }

    private void validateUsageLimits(
            CreateRebateRequest request
    ) {

        if (request.maxTotalUses() != null
                && request.maxUsesPerCustomer() != null
                && request.maxUsesPerCustomer()
                > request.maxTotalUses()) {

            throw new IllegalArgumentException(
                    "Maximum uses per customer cannot exceed total rebate usage limit."
            );
        }
    }

    private void validateScope(
            CreateRebateRequest request
    ) {

        if (request.scope()
                == RebateScope.CUSTOMER) {

            if (request.customerPhones() == null
                    || request.customerPhones()
                    .isEmpty()) {

                throw new IllegalArgumentException(
                        "Customer-specific rebate requires at least one customer phone."
                );
            }

            request.customerPhones()
                    .forEach(
                            this::normalizePhone
                    );

            return;
        }

        if (request.customerPhones() != null
                && !request.customerPhones()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "General rebate cannot contain customer assignments."
            );
        }
    }

    private void validatePercentage(
            CreateRebateRequest request
    ) {

        requirePositive(
                request.rebateValue(),
                "Percentage rebate value is required."
        );

        if (request.rebateValue()
                .compareTo(
                        BigDecimal.valueOf(100)
                ) > 0) {

            throw new IllegalArgumentException(
                    "Percentage rebate cannot exceed 100%."
            );
        }

        requirePositive(
                request.maximumDiscountAmount(),
                "Maximum rebate amount is required for percentage rebates."
        );

        validateMinimumAmount(
                request.minimumOrderAmount()
        );

        requireNoSlabs(request);
    }

    private void validateFixedAmount(
            CreateRebateRequest request
    ) {

        requirePositive(
                request.rebateValue(),
                "Fixed rebate amount is required."
        );

        validateMinimumAmount(
                request.minimumOrderAmount()
        );

        if (request.maximumDiscountAmount()
                != null) {

            throw new IllegalArgumentException(
                    "Maximum rebate amount must not be provided for fixed amount rebates."
            );
        }

        requireNoSlabs(request);
    }

    private void validateSlab(
            CreateRebateRequest request
    ) {

        if (request.rebateValue() != null
                || request.minimumOrderAmount() != null
                || request.maximumDiscountAmount() != null) {

            throw new IllegalArgumentException(
                    "Slab rebates must use slab rules instead of rebate value, minimum order amount, or maximum rebate amount."
            );
        }

        if (request.slabs() == null
                || request.slabs()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one rebate slab is required."
            );
        }

        validateSlabRules(
                request.slabs()
        );
    }

    // =========================================================
    // UPDATE VALIDATION
    // =========================================================

    private void validateUpdateRequest(
            UpdateRebateRequest request
    ) {

        if (!request.validUntil()
                .isAfter(
                        request.validFrom()
                )) {

            throw new IllegalArgumentException(
                    "Rebate end time must be after start time."
            );
        }

        if (request.maxTotalUses() != null
                && request.maxUsesPerCustomer() != null
                && request.maxUsesPerCustomer()
                > request.maxTotalUses()) {

            throw new IllegalArgumentException(
                    "Maximum uses per customer cannot exceed total usage limit."
            );
        }

        if (request.scope()
                == RebateScope.CUSTOMER) {

            if (request.customerPhones() == null
                    || request.customerPhones()
                    .isEmpty()) {

                throw new IllegalArgumentException(
                        "Customer-specific rebate requires at least one customer phone."
                );
            }

            request.customerPhones()
                    .forEach(
                            this::normalizePhone
                    );

        } else if (
                request.customerPhones() != null
                        && !request.customerPhones()
                        .isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "General rebate cannot contain customer assignments."
            );
        }

        switch (request.rebateType()) {

            case PERCENTAGE ->
                    validatePercentageUpdate(
                            request
                    );

            case FIXED_AMOUNT ->
                    validateFixedUpdate(
                            request
                    );

            case SLAB ->
                    validateSlabUpdate(
                            request
                    );
        }
    }

    private void validatePercentageUpdate(
            UpdateRebateRequest request
    ) {

        requirePositive(
                request.rebateValue(),
                "Percentage rebate value is required."
        );

        if (request.rebateValue()
                .compareTo(
                        BigDecimal.valueOf(100)
                ) > 0) {

            throw new IllegalArgumentException(
                    "Percentage rebate cannot exceed 100%."
            );
        }

        requirePositive(
                request.maximumDiscountAmount(),
                "Maximum rebate amount is required for percentage rebates."
        );

        validateMinimumAmount(
                request.minimumOrderAmount()
        );

        if (request.slabs() != null
                && !request.slabs()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "Percentage rebate cannot contain slabs."
            );
        }
    }

    private void validateFixedUpdate(
            UpdateRebateRequest request
    ) {

        requirePositive(
                request.rebateValue(),
                "Fixed rebate amount is required."
        );

        validateMinimumAmount(
                request.minimumOrderAmount()
        );

        if (request.maximumDiscountAmount()
                != null) {

            throw new IllegalArgumentException(
                    "Maximum rebate amount must not be provided for fixed amount rebates."
            );
        }

        if (request.slabs() != null
                && !request.slabs()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "Fixed rebate cannot contain slabs."
            );
        }
    }

    private void validateSlabUpdate(
            UpdateRebateRequest request
    ) {

        if (request.rebateValue() != null
                || request.minimumOrderAmount() != null
                || request.maximumDiscountAmount() != null) {

            throw new IllegalArgumentException(
                    "Slab rebate must use slab rules only."
            );
        }

        if (request.slabs() == null
                || request.slabs()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one rebate slab is required."
            );
        }

        validateSlabRules(
                request.slabs()
        );
    }

    /*
     * Shared validation used by CREATE and UPDATE.
     */
    private void validateSlabRules(
            List<RebateSlabRequest> slabs
    ) {

        Set<BigDecimal> thresholds =
                new HashSet<>();

        for (RebateSlabRequest slab : slabs) {

            BigDecimal minimum =
                    money(
                            slab.minimumOrderAmount()
                    );

            BigDecimal rebateAmount =
                    money(
                            slab.rebateAmount()
                    );

            if (minimum.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Slab minimum order amount must be greater than zero."
                );
            }

            if (rebateAmount.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new IllegalArgumentException(
                        "Slab rebate amount must be greater than zero."
                );
            }

            if (rebateAmount.compareTo(
                    minimum
            ) >= 0) {

                throw new IllegalArgumentException(
                        "Slab rebate amount must be less than its minimum order amount."
                );
            }

            if (!thresholds.add(minimum)) {

                throw new IllegalArgumentException(
                        "Duplicate slab minimum order amount is not allowed."
                );
            }
        }
    }

    // =========================================================
    // AUTHORIZATION
    // =========================================================

    private void requireViewAccess(
            Rebate rebate,
            StaffUser staff
    ) {

        if ("OWNER_ADMIN".equals(
                staff.getRole().getName()
        )) {

            return;
        }

        /*
         * Global rebates are visible to managers
         * because they apply across all branches.
         */
        if (rebate.getBranch() == null) {

            return;
        }

        staffAuthorizationService
                .requireBranchAccess(
                        rebate.getBranch().getId()
                );
    }

    private void requireManageAccess(
            Rebate rebate,
            StaffUser staff
    ) {

        if ("OWNER_ADMIN".equals(
                staff.getRole().getName()
        )) {

            return;
        }

        /*
         * Global rebates can only be managed
         * by OWNER_ADMIN.
         */
        if (rebate.getBranch() == null) {

            throw new AccessDeniedException(
                    "Only an owner can manage a global rebate."
            );
        }

        staffAuthorizationService
                .requireBranchAccess(
                        rebate.getBranch().getId()
                );
    }

    private boolean canView(
            Rebate rebate,
            StaffUser staff
    ) {

        if ("OWNER_ADMIN".equals(
                staff.getRole().getName()
        )) {

            return true;
        }

        if (rebate.getBranch() == null) {

            return true;
        }

        return staff.getBranches()
                .stream()
                .anyMatch(branch ->
                        branch.getId()
                                .equals(
                                        rebate.getBranch()
                                                .getId()
                                )
                );
    }

    private Branch resolveBranch(
            Long branchId,
            StaffUser currentStaff
    ) {

        /*
         * null branch = rebate valid for all branches.
         */
        if (branchId == null) {

            if (!"OWNER_ADMIN".equals(
                    currentStaff
                            .getRole()
                            .getName()
            )) {

                throw new AccessDeniedException(
                        "Only an owner can manage rebates for all branches."
                );
            }

            return null;
        }

        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );

        return branchRepository
                .findById(branchId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Branch does not exist."
                        )
                );
    }

    // =========================================================
    // CHILD ENTITY SAVING
    // =========================================================

    private List<RebateSlab> saveSlabs(
            Rebate rebate,
            CreateRebateRequest request
    ) {

        if (request.rebateType()
                != RebateType.SLAB) {

            return List.of();
        }

        List<RebateSlab> slabs =
                request.slabs()
                        .stream()

                        .sorted(
                                Comparator.comparing(
                                        RebateSlabRequest::minimumOrderAmount
                                )
                        )

                        .map(slabRequest -> {

                            RebateSlab slab =
                                    new RebateSlab();

                            slab.setRebate(rebate);

                            slab.setMinimumOrderAmount(
                                    money(
                                            slabRequest
                                                    .minimumOrderAmount()
                                    )
                            );

                            slab.setRebateAmount(
                                    money(
                                            slabRequest
                                                    .rebateAmount()
                                    )
                            );

                            return slab;
                        })

                        .toList();

        return rebateSlabRepository
                .saveAll(slabs);
    }

    private List<RebateCustomer> saveCustomers(
            Rebate rebate,
            CreateRebateRequest request
    ) {

        if (request.scope()
                != RebateScope.CUSTOMER) {

            return List.of();
        }

        Set<String> normalizedPhones =
                request.customerPhones()
                        .stream()

                        .map(
                                this::normalizePhone
                        )

                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        List<RebateCustomer> customers =
                new ArrayList<>();

        for (String phone : normalizedPhones) {

            RebateCustomer customer =
                    new RebateCustomer();

            customer.setRebate(rebate);

            customer.setCustomerPhone(
                    phone
            );

            customers.add(customer);
        }

        return rebateCustomerRepository
                .saveAll(customers);
    }

    private void saveUpdatedSlabs(
            Rebate rebate,
            List<RebateSlabRequest> requests
    ) {

        List<RebateSlab> slabs =
                requests.stream()

                        .sorted(
                                Comparator.comparing(
                                        RebateSlabRequest::minimumOrderAmount
                                )
                        )

                        .map(request -> {

                            RebateSlab slab =
                                    new RebateSlab();

                            slab.setRebate(
                                    rebate
                            );

                            slab.setMinimumOrderAmount(
                                    money(
                                            request.minimumOrderAmount()
                                    )
                            );

                            slab.setRebateAmount(
                                    money(
                                            request.rebateAmount()
                                    )
                            );

                            return slab;
                        })

                        .toList();

        rebateSlabRepository
                .saveAll(slabs);
    }

    private void saveUpdatedCustomers(
            Rebate rebate,
            Set<String> phones
    ) {

        Set<String> normalized =
                phones.stream()

                        .map(
                                this::normalizePhone
                        )

                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        List<RebateCustomer> customers =
                normalized.stream()

                        .map(phone -> {

                            RebateCustomer customer =
                                    new RebateCustomer();

                            customer.setRebate(
                                    rebate
                            );

                            customer.setCustomerPhone(
                                    phone
                            );

                            return customer;
                        })

                        .toList();

        rebateCustomerRepository
                .saveAll(customers);
    }

    // =========================================================
    // RESPONSE BUILDING
    // =========================================================

    private RebateResponse buildResponse(
            Rebate rebate
    ) {

        List<RebateSlab> slabs =
                rebateSlabRepository
                        .findByRebateIdOrderByMinimumOrderAmountAsc(
                                rebate.getId()
                        );

        List<RebateCustomer> customers =
                rebateCustomerRepository
                        .findByRebateId(
                                rebate.getId()
                        );

        return toResponse(
                rebate,
                slabs,
                customers
        );
    }

    private RebateResponse toResponse(
            Rebate rebate,
            List<RebateSlab> slabs,
            List<RebateCustomer> customers
    ) {

        List<RebateSlabResponse> slabResponses =
                slabs.stream()

                        .map(slab ->
                                new RebateSlabResponse(
                                        slab.getMinimumOrderAmount(),
                                        slab.getRebateAmount()
                                )
                        )

                        .toList();

        Set<String> phones =
                customers.stream()

                        .map(
                                RebateCustomer::getCustomerPhone
                        )

                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

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

                rebate.getBranch() == null
                        ? null
                        : rebate.getBranch().getId(),

                rebate.getBranch() == null
                        ? null
                        : rebate.getBranch().getName(),

                rebate.getValidFrom(),
                rebate.getValidUntil(),
                rebate.isActive(),

                slabResponses,

                phones,

                rebate.getCreatedBy()
                        .getFullName(),

                rebate.getCreatedAt(),
                rebate.getUpdatedAt()
        );
    }

    // =========================================================
    // DATABASE HELPERS
    // =========================================================

    private Rebate getRebate(
            Long rebateId
    ) {

        return rebateRepository
                .findById(rebateId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Rebate does not exist."
                        )
                );
    }

    // =========================================================
    // GENERAL VALIDATION HELPERS
    // =========================================================

    private void requireNoSlabs(
            CreateRebateRequest request
    ) {

        if (request.slabs() != null
                && !request.slabs()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "Slabs are only allowed for SLAB rebates."
            );
        }
    }

    private void requirePositive(
            BigDecimal value,
            String missingMessage
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                    missingMessage
            );
        }

        if (value.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Rebate amount must be greater than zero."
            );
        }
    }

    private void validateMinimumAmount(
            BigDecimal amount
    ) {

        if (amount != null
                && amount.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new IllegalArgumentException(
                    "Minimum order amount cannot be negative."
            );
        }
    }

    // =========================================================
    // NORMALIZATION
    // =========================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal moneyNullable(
            BigDecimal value
    ) {

        return value == null
                ? null
                : money(value);
    }

    private String normalizeCode(
            String code
    ) {

        return code
                .trim()
                .toUpperCase();
    }

    private String normalizePhone(
            String phone
    ) {

        if (phone == null) {

            throw new IllegalArgumentException(
                    "Customer phone is required."
            );
        }

        String normalized =
                phone.trim()
                        .replaceAll(
                                "\\s+",
                                ""
                        );

        if (!normalized.matches(
                "\\d{10}"
        )) {

            throw new IllegalArgumentException(
                    "Customer phone must contain exactly 10 digits."
            );
        }

        return normalized;
    }

    private String normalizeNullable(
            String value
    ) {

        if (value == null) {

            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
