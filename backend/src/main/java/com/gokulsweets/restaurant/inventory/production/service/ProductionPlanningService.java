package com.gokulsweets.restaurant.inventory.production.service;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.production.dto.*;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryLedgerService;
import com.gokulsweets.restaurant.inventory.service.InventoryQuantityService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordinates production planning operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionPlanningService {

    private final InventoryDailyAllocationRepository allocationRepository;

    private final BranchInventoryPolicyRepository policyRepository;

    private final InventoryQuantityService quantityService;

    private final InventoryLedgerService ledgerService;

    private final Clock inventoryClock;

    /**
     * Returns plan.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @return the get plan result
     */
    @Transactional(readOnly = true)
    public ProductionPlanResponse getPlan(Long branchId, LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductionPlanningService.class, "getPlan(Long,LocalDate)");
        try {
            if (branchId == null || serviceDate == null) {
                throw new IllegalArgumentException("Branch and service date are required.");
            }
            List<InventoryDailyAllocation> allocations =
                    allocationRepository
                            .findByBranchProduct_Branch_IdAndServiceDateOrderByBranchProduct_Product_NameAsc(
                                    branchId, serviceDate);
            List<Long> ids =
                    allocations.stream()
                            .map(allocation -> allocation.getBranchProduct().getId())
                            .toList();
            Map<Long, BranchInventoryPolicy> policies =
                    ids.isEmpty()
                            ? Map.of()
                            : policyRepository.findByBranchProductIdIn(ids).stream()
                                    .collect(
                                            Collectors.toMap(
                                                    policy -> policy.getBranchProduct().getId(),
                                                    Function.identity()));
            List<ProductionPlanItemResponse> items =
                    allocations.stream()
                            .map(
                                    allocation ->
                                            toItem(
                                                    allocation,
                                                    requirePolicy(
                                                            policies,
                                                            allocation.getBranchProduct().getId()),
                                                    serviceDate))
                            .sorted(
                                    Comparator.comparingInt(
                                                    (ProductionPlanItemResponse item) ->
                                                            priorityRank(item.priority()))
                                            .thenComparing(ProductionPlanItemResponse::categoryName)
                                            .thenComparing(ProductionPlanItemResponse::productName))
                            .toList();
            return new ProductionPlanResponse(
                    branchId,
                    serviceDate,
                    LocalDateTime.now(inventoryClock),
                    summary(items),
                    items);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "getPlan(Long,LocalDate)");
        }
    }

    /**
     * Records production.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param actor the actor
     * @return the record production result
     */
    @Transactional
    public ProductionPlanItemResponse recordProduction(
            Long branchProductId,
            LocalDate serviceDate,
            RecordProductionRequest request,
            String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "recordProduction(Long,LocalDate,RecordProductionRequest,String)");
        try {
            InventoryDailyAllocation allocation = lockAllocation(branchProductId, serviceDate);
            BranchInventoryPolicy policy = requirePolicy(branchProductId);
            requirePhysicalStockPolicy(policy);
            requireOperationalAllocation(allocation);
            BigDecimal quantity =
                    quantityService.normalizePositive(
                            request.quantity(), policy.getInventoryUnit(), "Produced quantity");
            allocation.setReadyQuantity(allocation.getReadyQuantity().add(quantity));
            allocation.setStatus(InventoryAllocationStatus.READY);
            allocation.setActualReadyAt(LocalDateTime.now(inventoryClock));
            allocation.setNote(normalize(request.note()));
            ledgerService.record(
                    allocation,
                    null,
                    InventoryTransactionType.READY_STOCK_RECORDED,
                    quantity,
                    null,
                    movementKey("production", allocation),
                    normalize(request.note()) == null
                            ? "Production recorded."
                            : request.note().trim(),
                    actor);
            log.info(
                    "Production recorded: branchProductId={}, serviceDate={}, quantity={},"
                            + " actor={}",
                    branchProductId,
                    serviceDate,
                    quantity,
                    actor);
            return toItem(allocation, policy, serviceDate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "recordProduction(Long,LocalDate,RecordProductionRequest,String)");
        }
    }

    /**
     * Records wastage.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param actor the actor
     * @return the record wastage result
     */
    @Transactional
    public ProductionPlanItemResponse recordWastage(
            Long branchProductId,
            LocalDate serviceDate,
            RecordWastageRequest request,
            String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "recordWastage(Long,LocalDate,RecordWastageRequest,String)");
        try {
            InventoryDailyAllocation allocation = lockAllocation(branchProductId, serviceDate);
            BranchInventoryPolicy policy = requirePolicy(branchProductId);
            requirePhysicalStockPolicy(policy);
            requireOperationalAllocation(allocation);
            BigDecimal quantity =
                    quantityService.normalizePositive(
                            request.quantity(), policy.getInventoryUnit(), "Wastage quantity");
            BigDecimal onHand = physicalOnHand(allocation);
            if (quantity.compareTo(onHand) > 0) {
                throw new InventoryConflictException(
                        "WASTAGE_EXCEEDS_STOCK",
                        "Wastage cannot exceed the physical stock currently on hand.");
            }
            allocation.setWastedQuantity(allocation.getWastedQuantity().add(quantity));
            allocation.setNote(request.reason().trim());
            ledgerService.record(
                    allocation,
                    null,
                    InventoryTransactionType.WASTAGE,
                    quantity.negate(),
                    null,
                    movementKey("wastage", allocation),
                    request.reason().trim(),
                    actor);
            log.info(
                    "Wastage recorded: branchProductId={}, serviceDate={}, quantity={}, actor={}",
                    branchProductId,
                    serviceDate,
                    quantity,
                    actor);
            return toItem(allocation, policy, serviceDate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "recordWastage(Long,LocalDate,RecordWastageRequest,String)");
        }
    }

    /**
     * Records adjustment.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param actor the actor
     * @return the record adjustment result
     */
    @Transactional
    public ProductionPlanItemResponse recordAdjustment(
            Long branchProductId,
            LocalDate serviceDate,
            RecordStockAdjustmentRequest request,
            String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "recordAdjustment(Long,LocalDate,RecordStockAdjustmentRequest,String)");
        try {
            InventoryDailyAllocation allocation = lockAllocation(branchProductId, serviceDate);
            BranchInventoryPolicy policy = requirePolicy(branchProductId);
            requirePhysicalStockPolicy(policy);
            requireOperationalAllocation(allocation);
            BigDecimal rawDelta = request.quantityDelta();
            if (rawDelta == null || rawDelta.signum() == 0) {
                throw new InventoryConflictException(
                        "ZERO_STOCK_ADJUSTMENT",
                        "Stock adjustment must increase or decrease stock.");
            }
            BigDecimal magnitude =
                    quantityService.normalizePositive(
                            rawDelta.abs(), policy.getInventoryUnit(), "Adjustment quantity");
            BigDecimal delta = rawDelta.signum() < 0 ? magnitude.negate() : magnitude;
            BigDecimal nextReady = allocation.getReadyQuantity().add(delta);
            BigDecimal consumed =
                    allocation.getFulfilledQuantity().add(allocation.getWastedQuantity());
            if (nextReady.compareTo(consumed) < 0) {
                throw new InventoryConflictException(
                        "ADJUSTMENT_BELOW_CONSUMED_STOCK",
                        "Adjusted ready stock cannot be lower than fulfilled and wasted"
                                + " quantities.");
            }
            allocation.setReadyQuantity(nextReady);
            allocation.setNote(request.reason().trim());
            ledgerService.record(
                    allocation,
                    null,
                    InventoryTransactionType.ADJUSTMENT,
                    delta,
                    null,
                    movementKey("adjustment", allocation),
                    request.reason().trim(),
                    actor);
            log.info(
                    "Stock adjustment recorded: branchProductId={}, serviceDate={}, delta={},"
                            + " actor={}",
                    branchProductId,
                    serviceDate,
                    delta,
                    actor);
            return toItem(allocation, policy, serviceDate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "recordAdjustment(Long,LocalDate,RecordStockAdjustmentRequest,String)");
        }
    }

    /**
     * Tos item.
     *
     * @param allocation the allocation
     * @param policy the policy
     * @param serviceDate the service date
     * @return the to item result
     */
    private ProductionPlanItemResponse toItem(
            InventoryDailyAllocation allocation,
            BranchInventoryPolicy policy,
            LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "toItem(InventoryDailyAllocation,BranchInventoryPolicy,LocalDate)");
        try {
            BigDecimal zero = BigDecimal.ZERO.setScale(3);
            BigDecimal fulfilled = allocation.getFulfilledQuantity();
            BigDecimal outstanding =
                    allocation.getCommittedQuantity().subtract(fulfilled).max(zero);
            BigDecimal onHand = physicalOnHand(allocation).max(zero);
            BigDecimal forecast =
                    allocation.getForecastQuantity() == null
                            ? zero
                            : allocation.getForecastQuantity();
            BigDecimal minimumTarget =
                    allocation
                            .getCommittedQuantity()
                            .add(allocation.getSafetyBufferQuantity())
                            .min(allocation.getApprovedQuantity());
            BigDecimal suggestedTarget =
                    allocation
                            .getCommittedQuantity()
                            .max(forecast)
                            .add(allocation.getSafetyBufferQuantity())
                            .min(allocation.getApprovedQuantity());
            BigDecimal minimumToPrepare =
                    minimumTarget
                            .add(allocation.getWastedQuantity())
                            .subtract(allocation.getReadyQuantity())
                            .max(zero);
            BigDecimal suggestedToPrepare =
                    suggestedTarget
                            .add(allocation.getWastedQuantity())
                            .subtract(allocation.getReadyQuantity())
                            .max(zero);
            String priority =
                    priority(
                            allocation,
                            minimumToPrepare,
                            suggestedToPrepare,
                            serviceDate,
                            physicalOnHand(allocation));
            return new ProductionPlanItemResponse(
                    allocation.getBranchProduct().getId(),
                    allocation.getBranchProduct().getProduct().getId(),
                    allocation.getBranchProduct().getProduct().getCode(),
                    allocation.getBranchProduct().getProduct().getName(),
                    allocation.getBranchProduct().getProduct().getCategory().getName(),
                    allocation.getInventoryUnit().name(),
                    policy.getControlMode().name(),
                    allocation.getStatus().name(),
                    priority,
                    allocation.getApprovedQuantity(),
                    allocation.getHeldQuantity(),
                    allocation.getCommittedQuantity(),
                    outstanding,
                    allocation.getReadyQuantity(),
                    fulfilled,
                    allocation.getWastedQuantity(),
                    onHand,
                    allocation.getSafetyBufferQuantity(),
                    forecast,
                    allocation.getForecastConfidence(),
                    minimumToPrepare,
                    suggestedToPrepare,
                    allocation.getExpectedReadyAt(),
                    allocation.getNote());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "toItem(InventoryDailyAllocation,BranchInventoryPolicy,LocalDate)");
        }
    }

    /**
     * Returns priority information for production planning.
     *
     * @param allocation the allocation supplied to this method
     * @param minimum the minimum supplied to this method
     * @param suggested the suggested supplied to this method
     * @param serviceDate the service date supplied to this method
     * @param rawOnHand the raw on hand supplied to this method
     * @return the {@code String} result
     */
    private String priority(
            InventoryDailyAllocation allocation,
            BigDecimal minimum,
            BigDecimal suggested,
            LocalDate serviceDate,
            BigDecimal rawOnHand) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "priority(InventoryDailyAllocation,BigDecimal,BigDecimal,LocalDate,BigDecimal)");
        try {
            if (rawOnHand.signum() < 0) return "DISCREPANCY";
            if (allocation.getStatus() == InventoryAllocationStatus.DELAYED) return "DELAYED";
            if (minimum.signum() > 0 && !serviceDate.isAfter(LocalDate.now(inventoryClock))) {
                return "CRITICAL";
            }
            if (minimum.signum() > 0) return "NEEDS_PRODUCTION";
            if (suggested.signum() > 0) return "FORECAST_TOP_UP";
            return "READY";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "priority(InventoryDailyAllocation,BigDecimal,BigDecimal,LocalDate,BigDecimal)");
        }
    }

    /**
     * Returns summary information for production planning.
     *
     * @param items the items supplied to this method
     * @return the {@code ProductionPlanSummaryResponse} result
     */
    private ProductionPlanSummaryResponse summary(List<ProductionPlanItemResponse> items) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "summary(List<ProductionPlanItemResponse>)");
        try {
            return new ProductionPlanSummaryResponse(
                    items.size(),
                    count(items, "CRITICAL"),
                    count(items, "NEEDS_PRODUCTION"),
                    count(items, "FORECAST_TOP_UP"),
                    count(items, "READY"),
                    count(items, "DELAYED"),
                    count(items, "DISCREPANCY"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "summary(List<ProductionPlanItemResponse>)");
        }
    }

    /**
     * Returns count information for production planning.
     *
     * @param items the items supplied to this method
     * @param priority the priority supplied to this method
     * @return the value of {@code items.stream().filter(item ->
     *     priority.equals(item.priority())).count()}
     */
    private long count(List<ProductionPlanItemResponse> items, String priority) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "count(List<ProductionPlanItemResponse>,String)");
        try {
            return items.stream().filter(item -> priority.equals(item.priority())).count();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "count(List<ProductionPlanItemResponse>,String)");
        }
    }

    /**
     * Priority rank.
     *
     * @param priority the priority
     * @return the priority rank result
     */
    private int priorityRank(String priority) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductionPlanningService.class, "priorityRank(String)");
        try {
            return switch (priority) {
                case "DISCREPANCY" -> 0;
                case "CRITICAL" -> 1;
                case "DELAYED" -> 2;
                case "NEEDS_PRODUCTION" -> 3;
                case "FORECAST_TOP_UP" -> 4;
                default -> 5;
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "priorityRank(String)");
        }
    }

    /**
     * Physicals on hand.
     *
     * @param allocation the allocation
     * @return the physical on hand result
     */
    private BigDecimal physicalOnHand(InventoryDailyAllocation allocation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "physicalOnHand(InventoryDailyAllocation)");
        try {
            return allocation
                    .getReadyQuantity()
                    .subtract(allocation.getFulfilledQuantity())
                    .subtract(allocation.getWastedQuantity());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "physicalOnHand(InventoryDailyAllocation)");
        }
    }

    /**
     * Locks allocation.
     *
     * @param branchProductId the branch product id
     * @param date the date
     * @return the lock allocation result
     */
    private InventoryDailyAllocation lockAllocation(Long branchProductId, LocalDate date) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class, "lockAllocation(Long,LocalDate)");
        try {
            return allocationRepository
                    .findForUpdate(branchProductId, date)
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "ALLOCATION_NOT_FOUND",
                                            "No inventory allocation exists for this product and"
                                                    + " date."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "lockAllocation(Long,LocalDate)");
        }
    }

    /**
     * Requires policy.
     *
     * @param branchProductId the branch product id
     * @return the require policy result
     */
    private BranchInventoryPolicy requirePolicy(Long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductionPlanningService.class, "requirePolicy(Long)");
        try {
            return policyRepository
                    .findByBranchProductId(branchProductId)
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "INVENTORY_POLICY_NOT_FOUND",
                                            "Inventory policy is not configured for this"
                                                    + " product."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "requirePolicy(Long)");
        }
    }

    /**
     * Requires policy.
     *
     * @param policies the policies
     * @param branchProductId the branch product id
     * @return the require policy result
     */
    private BranchInventoryPolicy requirePolicy(
            Map<Long, BranchInventoryPolicy> policies, Long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "requirePolicy(Map<Long,BranchInventoryPolicy>,Long)");
        try {
            BranchInventoryPolicy policy = policies.get(branchProductId);
            if (policy == null)
                throw new InventoryNotFoundException(
                        "INVENTORY_POLICY_NOT_FOUND",
                        "Inventory policy is missing for an allocated product.");
            return policy;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "requirePolicy(Map<Long,BranchInventoryPolicy>,Long)");
        }
    }

    /**
     * Requires operational allocation.
     *
     * @param allocation the allocation
     */
    private void requireOperationalAllocation(InventoryDailyAllocation allocation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "requireOperationalAllocation(InventoryDailyAllocation)");
        try {
            if (allocation.getStatus() == InventoryAllocationStatus.DRAFT
                    || allocation.getStatus() == InventoryAllocationStatus.UNAVAILABLE
                    || allocation.getStatus() == InventoryAllocationStatus.CLOSED) {
                throw new InventoryConflictException(
                        "ALLOCATION_NOT_OPERATIONAL",
                        "Approve and open the allocation before recording stock movements.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "requireOperationalAllocation(InventoryDailyAllocation)");
        }
    }

    /**
     * Requires physical stock policy.
     *
     * @param policy the policy
     */
    private void requirePhysicalStockPolicy(BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "requirePhysicalStockPolicy(BranchInventoryPolicy)");
        try {
            if (policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY) {
                throw new InventoryConflictException(
                        "PHYSICAL_MOVEMENT_NOT_SUPPORTED",
                        "Capacity-managed products must be updated through pickup slot capacity.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "requirePhysicalStockPolicy(BranchInventoryPolicy)");
        }
    }

    /**
     * Movements key.
     *
     * @param prefix the prefix
     * @param allocation the allocation
     * @return the movement key result
     */
    private String movementKey(String prefix, InventoryDailyAllocation allocation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductionPlanningService.class,
                        "movementKey(String,InventoryDailyAllocation)");
        try {
            return prefix + ":" + allocation.getId() + ":" + UUID.randomUUID();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "movementKey(String,InventoryDailyAllocation)");
        }
    }

    /**
     * Normalizes production planning data and returns the {@code String} result.
     *
     * @param value the value supplied to this method
     * @return the value of {@code value == null || value.isBlank() ? null : value.trim()}
     */
    private String normalize(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductionPlanningService.class, "normalize(String)");
        try {
            return value == null || value.isBlank() ? null : value.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductionPlanningService.class,
                    "normalize(String)");
        }
    }
}
