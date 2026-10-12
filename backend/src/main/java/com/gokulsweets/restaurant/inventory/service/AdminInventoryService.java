package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Coordinates admin inventory operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminInventoryService {

    /**
     * Immutable plan change data contract.
     *
     * @param id the id
     * @param performedBy the performed by
     * @param changedAt the changed at
     * @param beforeState the before state
     * @param afterState the after state
     */
    public record PlanChange(
            long id,
            String performedBy,
            LocalDateTime changedAt,
            String beforeState,
            String afterState) {}

    private final BranchProductRepository branchProductRepository;

    private final BranchInventoryPolicyRepository policyRepository;

    private final InventoryDailyAllocationRepository allocationRepository;

    private final InventoryAvailabilityService availabilityService;

    private final InventoryLedgerService ledgerService;

    private final InventoryQuantityService quantityService;

    private final InventoryProperties properties;

    private final Clock inventoryClock;

    private final JdbcTemplate jdbc;

    /**
     * Returns plan history.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @return the get plan history result
     */
    @Transactional(readOnly = true)
    public List<PlanChange> getPlanHistory(Long branchProductId, LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryService.class, "getPlanHistory(Long,LocalDate)");
        try {
            return jdbc.query(
                    """
                    SELECT id, performed_by, changed_at, before_state, after_state
                    FROM inventory_allocation_plan_audit
                    WHERE branch_product_id = ? AND service_date = ?
                    ORDER BY changed_at DESC, id DESC LIMIT 30
                    """,
                    (rs, row) ->
                            new PlanChange(
                                    rs.getLong("id"),
                                    rs.getString("performed_by"),
                                    rs.getTimestamp("changed_at").toLocalDateTime(),
                                    rs.getString("before_state"),
                                    rs.getString("after_state")),
                    branchProductId,
                    serviceDate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "getPlanHistory(Long,LocalDate)");
        }
    }

    /**
     * Upserts policy.
     *
     * @param branchProductId the branch product id
     * @param request the request
     * @return the upsert policy result
     */
    @Transactional
    public InventoryPolicyResponse upsertPolicy(
            Long branchProductId, AdminInventoryPolicyRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "upsertPolicy(Long,AdminInventoryPolicyRequest)");
        try {
            BranchProduct branchProduct =
                    branchProductRepository
                            .findById(branchProductId)
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundException(
                                                    "BRANCH_PRODUCT_NOT_FOUND",
                                                    "The selected branch product does not exist."));
            validatePolicyRequest(request, branchProduct);
            BranchInventoryPolicy policy =
                    policyRepository
                            .findByBranchProductId(branchProductId)
                            .orElseGet(BranchInventoryPolicy::new);
            policy.setBranchProduct(branchProduct);
            policy.setControlMode(request.controlMode());
            policy.setInventoryUnit(request.inventoryUnit());
            policy.setOnlineEnabled(request.onlineEnabled());
            policy.setReadyStockRequired(request.readyStockRequired());
            policy.setDefaultSafetyBuffer(
                    quantityService.normalizeNonNegative(
                            request.defaultSafetyBuffer(),
                            request.inventoryUnit(),
                            "Default safety buffer"));
            policy.setMaximumDailyAllocation(
                    quantityService.normalizeNonNegative(
                            request.maximumDailyAllocation(),
                            request.inventoryUnit(),
                            "Maximum daily allocation"));
            policy.setBookingHorizonDays(
                    request.bookingHorizonDays() == null
                            ? properties.getDefaultBookingHorizonDays()
                            : request.bookingHorizonDays());
            policy.setProductionLeadMinutes(request.productionLeadMinutes());
            policy.setShelfLifeMinutes(request.shelfLifeMinutes());
            BranchInventoryPolicy saved = policyRepository.save(policy);
            log.info(
                    "Inventory policy saved: branchProductId={}, controlMode={}, unit={},"
                            + " onlineEnabled={}",
                    branchProductId,
                    saved.getControlMode(),
                    saved.getInventoryUnit(),
                    saved.isOnlineEnabled());
            return InventoryPolicyResponse.from(saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "upsertPolicy(Long,AdminInventoryPolicyRequest)");
        }
    }

    /**
     * Approves allocation.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param performedBy the performed by
     * @return the approve allocation result
     */
    @Transactional
    public InventoryAllocationResponse approveAllocation(
            Long branchProductId,
            LocalDate serviceDate,
            AdminAllocationApprovalRequest request,
            String performedBy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "approveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String)");
        try {
            return saveAllocation(branchProductId, serviceDate, request, performedBy, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "approveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String)");
        }
    }

    /**
     * Adjust quantities without resuming paused stock or revoking existing readiness.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param performedBy the performed by
     * @return the operation result
     */
    @Transactional
    public InventoryAllocationResponse adjustAllocation(
            Long branchProductId,
            LocalDate serviceDate,
            AdminAllocationApprovalRequest request,
            String performedBy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "adjustAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String)");
        try {
            return saveAllocation(branchProductId, serviceDate, request, performedBy, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "adjustAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String)");
        }
    }

    /**
     * Saves allocation.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param performedBy the performed by
     * @param preserveReadiness the preserve readiness
     * @return the save allocation result
     */
    private InventoryAllocationResponse saveAllocation(
            Long branchProductId,
            LocalDate serviceDate,
            AdminAllocationApprovalRequest request,
            String performedBy,
            boolean preserveReadiness) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "saveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String,boolean)");
        try {
            BranchInventoryPolicy policy = getPolicy(branchProductId);
            validateServiceDate(serviceDate, policy);
            BigDecimal approvedQuantity =
                    quantityService.normalizePositive(
                            request.approvedQuantity(),
                            policy.getInventoryUnit(),
                            "Approved quantity");
            BigDecimal safetyBuffer =
                    request.safetyBufferQuantity() == null
                            ? policy.getDefaultSafetyBuffer()
                            : quantityService.normalizeNonNegative(
                                    request.safetyBufferQuantity(),
                                    policy.getInventoryUnit(),
                                    "Safety buffer");
            validateApprovedQuantity(approvedQuantity, policy);
            InventoryDailyAllocation allocation =
                    allocationRepository
                            .findForUpdate(branchProductId, serviceDate)
                            .orElseGet(() -> newAllocation(policy, serviceDate));
            ensureCommittedQuantityStillCovered(allocation, approvedQuantity, safetyBuffer);
            BigDecimal previousApproved = allocation.getApprovedQuantity();
            String beforePlan = planSnapshot(allocation);
            allocation.setApprovedQuantity(approvedQuantity);
            allocation.setSafetyBufferQuantity(safetyBuffer);
            allocation.setForecastQuantity(
                    quantityService.normalizeNonNegative(
                            request.forecastQuantity(),
                            policy.getInventoryUnit(),
                            "Forecast quantity"));
            allocation.setForecastConfidence(normalize(request.forecastConfidence()));
            allocation.setExpectedReadyAt(request.expectedReadyAt());
            allocation.setNote(normalize(request.note()));
            allocation.setApprovedBy(performedBy);
            allocation.setApprovedAt(LocalDateTime.now(inventoryClock));
            if (!preserveReadiness || allocation.getStatus() == InventoryAllocationStatus.DRAFT) {
                allocation.setStatus(InventoryAllocationStatus.APPROVED);
            }
            InventoryDailyAllocation saved = allocationRepository.save(allocation);
            jdbc.update(
                    """
INSERT INTO inventory_allocation_plan_audit
    (allocation_id, branch_product_id, service_date, performed_by, before_state, after_state)
VALUES (?, ?, ?, ?, ?, ?)
""",
                    saved.getId(),
                    branchProductId,
                    serviceDate,
                    performedBy,
                    beforePlan,
                    planSnapshot(saved));
            BigDecimal delta = approvedQuantity.subtract(previousApproved);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                ledgerService.record(
                        saved,
                        null,
                        InventoryTransactionType.ALLOCATION_APPROVED,
                        delta,
                        null,
                        "allocation:" + saved.getId(),
                        "Daily online allocation approved.",
                        performedBy);
            }
            log.info(
                    "Inventory allocation approved: branchProductId={}, serviceDate={},"
                            + " approvedQuantity={}, safetyBuffer={}",
                    branchProductId,
                    serviceDate,
                    saved.getApprovedQuantity(),
                    saved.getSafetyBufferQuantity());
            return toResponse(saved, policy);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "saveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,String,boolean)");
        }
    }

    /**
     * Plans snapshot.
     *
     * @param allocation the allocation
     * @return the plan snapshot result
     */
    private static String planSnapshot(InventoryDailyAllocation allocation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class, "planSnapshot(InventoryDailyAllocation)");
        try {
            return "status="
                    + allocation.getStatus()
                    + "; approved="
                    + allocation.getApprovedQuantity()
                    + "; ready="
                    + allocation.getReadyQuantity()
                    + "; buffer="
                    + allocation.getSafetyBufferQuantity()
                    + "; forecast="
                    + allocation.getForecastQuantity()
                    + "; confidence="
                    + allocation.getForecastConfidence()
                    + "; expectedReady="
                    + allocation.getExpectedReadyAt();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "planSnapshot(InventoryDailyAllocation)");
        }
    }

    /**
     * Updates readiness.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param performedBy the performed by
     * @return the update readiness result
     */
    @Transactional
    public InventoryAllocationResponse updateReadiness(
            Long branchProductId,
            LocalDate serviceDate,
            AdminReadinessUpdateRequest request,
            String performedBy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "updateReadiness(Long,LocalDate,AdminReadinessUpdateRequest,String)");
        try {
            if (request.status() != InventoryAllocationStatus.READY
                    && request.status() != InventoryAllocationStatus.DELAYED
                    && request.status() != InventoryAllocationStatus.UNAVAILABLE) {
                throw new InventoryConflictException(
                        "INVALID_READINESS_STATUS",
                        "Readiness can only be READY, DELAYED or UNAVAILABLE.");
            }
            BranchInventoryPolicy policy = getPolicy(branchProductId);
            InventoryDailyAllocation allocation =
                    allocationRepository
                            .findForUpdate(branchProductId, serviceDate)
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundException(
                                                    "ALLOCATION_NOT_FOUND",
                                                    "No inventory allocation exists for this"
                                                            + " product and date."));
            BigDecimal readyQuantity =
                    quantityService.normalizeNonNegative(
                            request.readyQuantity(), policy.getInventoryUnit(), "Ready quantity");
            BigDecimal previousReady = allocation.getReadyQuantity();
            String beforePlan = planSnapshot(allocation);
            if (request.status() == InventoryAllocationStatus.READY
                    && readyQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InventoryConflictException(
                        "READY_QUANTITY_REQUIRED",
                        "Ready quantity must be greater than zero when stock is marked ready.");
            }
            BigDecimal protectedQuantity =
                    allocation.getHeldQuantity().add(allocation.getCommittedQuantity());
            if (policy.isReadyStockRequired() && readyQuantity.compareTo(protectedQuantity) < 0) {
                throw new InventoryConflictException(
                        "READY_QUANTITY_BELOW_COMMITMENTS",
                        "Ready quantity cannot be lower than existing holds and confirmed"
                                + " commitments.");
            }
            if (request.status() == InventoryAllocationStatus.UNAVAILABLE
                    && protectedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                throw new InventoryConflictException(
                        "ALLOCATION_HAS_COMMITMENTS",
                        "This allocation has customer commitments. Resolve the affected orders"
                                + " before marking it unavailable.");
            }
            allocation.setStatus(request.status());
            allocation.setReadyQuantity(readyQuantity);
            allocation.setExpectedReadyAt(request.expectedReadyAt());
            allocation.setActualReadyAt(
                    request.status() == InventoryAllocationStatus.READY
                            ? LocalDateTime.now(inventoryClock)
                            : null);
            allocation.setNote(normalize(request.note()));
            InventoryDailyAllocation saved = allocationRepository.save(allocation);
            jdbc.update(
                    """
INSERT INTO inventory_allocation_plan_audit
    (allocation_id, branch_product_id, service_date, performed_by, before_state, after_state)
VALUES (?, ?, ?, ?, ?, ?)
""",
                    saved.getId(),
                    branchProductId,
                    serviceDate,
                    performedBy,
                    beforePlan,
                    planSnapshot(saved));
            BigDecimal delta = readyQuantity.subtract(previousReady);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                ledgerService.record(
                        saved,
                        null,
                        InventoryTransactionType.READY_STOCK_RECORDED,
                        delta,
                        null,
                        "readiness:" + saved.getId(),
                        "Ready stock updated.",
                        performedBy);
            }
            return toResponse(saved, policy);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "updateReadiness(Long,LocalDate,AdminReadinessUpdateRequest,String)");
        }
    }

    /**
     * Returns allocations.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @return the get allocations result
     */
    @Transactional(readOnly = true)
    public List<InventoryAllocationResponse> getAllocations(Long branchId, LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryService.class, "getAllocations(Long,LocalDate)");
        try {
            return allocationRepository
                    .findByBranchProduct_Branch_IdAndServiceDateOrderByBranchProduct_Product_NameAsc(
                            branchId, serviceDate)
                    .stream()
                    .map(
                            allocation -> {
                                BranchInventoryPolicy policy =
                                        getPolicy(allocation.getBranchProduct().getId());
                                return toResponse(allocation, policy);
                            })
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "getAllocations(Long,LocalDate)");
        }
    }

    /**
     * Tos response.
     *
     * @param allocation the allocation
     * @param policy the policy
     * @return the to response result
     */
    private InventoryAllocationResponse toResponse(
            InventoryDailyAllocation allocation, BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "toResponse(InventoryDailyAllocation,BranchInventoryPolicy)");
        try {
            InventoryAvailability availability = availabilityService.calculate(allocation, policy);
            return InventoryAllocationResponse.from(allocation, availability);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "toResponse(InventoryDailyAllocation,BranchInventoryPolicy)");
        }
    }

    /**
     * News allocation.
     *
     * @param policy the policy
     * @param serviceDate the service date
     * @return the new allocation result
     */
    private InventoryDailyAllocation newAllocation(
            BranchInventoryPolicy policy, LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "newAllocation(BranchInventoryPolicy,LocalDate)");
        try {
            InventoryDailyAllocation allocation = new InventoryDailyAllocation();
            allocation.setBranchProduct(policy.getBranchProduct());
            allocation.setServiceDate(serviceDate);
            allocation.setInventoryUnit(policy.getInventoryUnit());
            allocation.setSafetyBufferQuantity(policy.getDefaultSafetyBuffer());
            return allocation;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "newAllocation(BranchInventoryPolicy,LocalDate)");
        }
    }

    /**
     * Returns policy.
     *
     * @param branchProductId the branch product id
     * @return the get policy result
     */
    private BranchInventoryPolicy getPolicy(Long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryService.class, "getPolicy(Long)");
        try {
            return policyRepository
                    .findByBranchProductId(branchProductId)
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "INVENTORY_POLICY_NOT_FOUND",
                                            "Inventory policy is not configured for this branch"
                                                    + " product."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminInventoryService.class, "getPolicy(Long)");
        }
    }

    /**
     * Validates service date.
     *
     * @param serviceDate the service date
     * @param policy the policy
     */
    private void validateServiceDate(LocalDate serviceDate, BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "validateServiceDate(LocalDate,BranchInventoryPolicy)");
        try {
            LocalDate today = LocalDate.now(inventoryClock);
            LocalDate lastAllowed = today.plusDays(policy.getBookingHorizonDays());
            if (serviceDate.isBefore(today)) {
                throw new InventoryConflictException(
                        "PAST_ALLOCATION_DATE", "Inventory cannot be approved for a past date.");
            }
            if (serviceDate.isAfter(lastAllowed)) {
                throw new InventoryConflictException(
                        "ALLOCATION_OUTSIDE_BOOKING_HORIZON",
                        "The selected date is outside this product's booking horizon.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "validateServiceDate(LocalDate,BranchInventoryPolicy)");
        }
    }

    /**
     * Validates approved quantity.
     *
     * @param quantity the quantity
     * @param policy the policy
     */
    private void validateApprovedQuantity(BigDecimal quantity, BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "validateApprovedQuantity(BigDecimal,BranchInventoryPolicy)");
        try {
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InventoryConflictException(
                        "APPROVED_QUANTITY_REQUIRED",
                        "Approved quantity must be greater than zero.");
            }
            if (policy.getMaximumDailyAllocation() != null
                    && quantity.compareTo(policy.getMaximumDailyAllocation()) > 0) {
                throw new InventoryConflictException(
                        "MAXIMUM_ALLOCATION_EXCEEDED",
                        "Approved quantity exceeds the configured daily maximum.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "validateApprovedQuantity(BigDecimal,BranchInventoryPolicy)");
        }
    }

    /**
     * Ensures committed quantity still covered.
     *
     * @param allocation the allocation
     * @param approvedQuantity the approved quantity
     * @param buffer the buffer
     */
    private void ensureCommittedQuantityStillCovered(
            InventoryDailyAllocation allocation, BigDecimal approvedQuantity, BigDecimal buffer) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "ensureCommittedQuantityStillCovered(InventoryDailyAllocation,BigDecimal,BigDecimal)");
        try {
            BigDecimal protectedQuantity =
                    allocation.getHeldQuantity().add(allocation.getCommittedQuantity()).add(buffer);
            if (approvedQuantity.compareTo(protectedQuantity) < 0) {
                throw new InventoryConflictException(
                        "ALLOCATION_BELOW_COMMITMENTS",
                        "Approved quantity cannot be lower than existing holds, commitments and"
                                + " safety buffer.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "ensureCommittedQuantityStillCovered(InventoryDailyAllocation,BigDecimal,BigDecimal)");
        }
    }

    /**
     * Validates policy request.
     *
     * @param request the request
     * @param branchProduct the branch product
     */
    private void validatePolicyRequest(
            AdminInventoryPolicyRequest request, BranchProduct branchProduct) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryService.class,
                        "validatePolicyRequest(AdminInventoryPolicyRequest,BranchProduct)");
        try {
            quantityService.validatePolicyUnit(
                    branchProduct.getProduct().getSaleMode(),
                    request.controlMode(),
                    request.inventoryUnit());
            if (request.maximumDailyAllocation() != null
                    && request.defaultSafetyBuffer().compareTo(request.maximumDailyAllocation())
                            >= 0) {
                throw new InventoryConflictException(
                        "INVALID_SAFETY_BUFFER",
                        "Safety buffer must be lower than the maximum daily allocation.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryService.class,
                    "validatePolicyRequest(AdminInventoryPolicyRequest,BranchProduct)");
        }
    }

    /**
     * Normalizes admin inventory data and returns the {@code String} result.
     *
     * @param value the value supplied to this method
     * @return the {@code String} result
     */
    private String normalize(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryService.class, "normalize(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminInventoryService.class, "normalize(String)");
        }
    }
}
