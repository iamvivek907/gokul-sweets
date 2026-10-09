package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckResponse;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordinates customer inventory availability operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerInventoryAvailabilityService {

    private final BranchRepository branchRepository;

    private final BranchProductRepository branchProductRepository;

    private final BranchInventoryPolicyRepository policyRepository;

    private final InventoryDailyAllocationRepository allocationRepository;

    private final InventoryAvailabilityService availabilityService;

    private final InventoryQuantityService quantityService;

    private final InventoryProperties properties;

    private final Clock inventoryClock;

    /**
     * Returns check information for customer inventory availability.
     *
     * @param branchId the branch id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code check(branchId, request, true)}
     */
    @Transactional(readOnly = true)
    public CustomerInventoryCheckResponse check(
            Long branchId, CustomerInventoryCheckRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "check(Long,CustomerInventoryCheckRequest)");
        try {
            return check(branchId, request, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "check(Long,CustomerInventoryCheckRequest)");
        }
    }

    /**
     * Discovery only needs this date; do not scan future dates for every candidate.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the operation result
     */
    @Transactional(readOnly = true)
    public CustomerInventoryCheckResponse checkRequestedDate(
            Long branchId, CustomerInventoryCheckRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "checkRequestedDate(Long,CustomerInventoryCheckRequest)");
        try {
            return check(branchId, request, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "checkRequestedDate(Long,CustomerInventoryCheckRequest)");
        }
    }

    /**
     * Returns check information for customer inventory availability.
     *
     * <p>Delegates to {@code branchProductRepository.findForOrder(...)}, {@code
     * policyRepository.findByBranchProductIdIn(...)}, {@code
     * allocationRepository.findByBranchProductIdInAndServiceDate(...)}, {@code
     * allocationRepository.findByBranchProductIdInAndServiceDateBetween(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param request the request supplied to this method
     * @param suggestDate the suggest date supplied to this method
     * @return the {@code CustomerInventoryCheckResponse} result
     * @throws IllegalArgumentException when the method rejects the request with {@code One or more
     *     selected products are not sold by this branch.}; {@code Pickup date cannot be in the
     *     past.}
     */
    private CustomerInventoryCheckResponse check(
            Long branchId, CustomerInventoryCheckRequest request, boolean suggestDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "check(Long,CustomerInventoryCheckRequest,boolean)");
        try {
            Branch branch = requireActiveBranch(branchId);
            LocalDate today = LocalDate.now(inventoryClock);
            if (request.serviceDate().isBefore(today)) {
                throw new IllegalArgumentException("Pickup date cannot be in the past.");
            }
            Map<Long, CustomerInventoryCheckRequest.Item> requested =
                    normalizeItems(request.items());
            List<BranchProduct> branchProducts =
                    branchProductRepository.findForOrder(branch.getId(), requested.keySet());
            if (branchProducts.size() != requested.size()) {
                throw new IllegalArgumentException(
                        "One or more selected products are not sold by this branch.");
            }
            Map<Long, BranchProduct> byProductId =
                    branchProducts.stream()
                            .collect(
                                    Collectors.toMap(
                                            value -> value.getProduct().getId(),
                                            Function.identity()));
            if (!properties.isEnforcementEnabled()) {
                return new CustomerInventoryCheckResponse(
                        false, request.serviceDate(), true, null, false, List.of());
            }
            List<Long> branchProductIds =
                    branchProducts.stream().map(BranchProduct::getId).toList();
            Map<Long, BranchInventoryPolicy> policies =
                    policyRepository.findByBranchProductIdIn(branchProductIds).stream()
                            .collect(
                                    Collectors.toMap(
                                            policy -> policy.getBranchProduct().getId(),
                                            Function.identity()));
            DateResult requestedResult =
                    evaluateDate(
                            request.serviceDate(),
                            requested,
                            byProductId,
                            policies,
                            allocationRepository.findByBranchProductIdInAndServiceDate(
                                    branchProductIds, request.serviceDate()));
            LocalDate suggestedDate = null;
            if (suggestDate && !requestedResult.orderable()) {
                int searchDays = maximumSearchDays(policies.values());
                LocalDate lastDate = today.plusDays(searchDays);
                LocalDate batchThrough = request.serviceDate();
                Map<LocalDate, List<InventoryDailyAllocation>> batch = Map.of();
                for (LocalDate candidate = request.serviceDate().plusDays(1);
                        !candidate.isAfter(lastDate);
                        candidate = candidate.plusDays(1)) {
                    // Bounded seven-day batches avoid one remote query per candidate date.
                    if (candidate.isAfter(batchThrough)) {
                        batchThrough =
                                candidate.plusDays(6).isAfter(lastDate)
                                        ? lastDate
                                        : candidate.plusDays(6);
                        batch =
                                allocationRepository
                                        .findByBranchProductIdInAndServiceDateBetween(
                                                branchProductIds, candidate, batchThrough)
                                        .stream()
                                        .collect(
                                                Collectors.groupingBy(
                                                        InventoryDailyAllocation::getServiceDate));
                    }
                    if (evaluateDate(
                                    candidate,
                                    requested,
                                    byProductId,
                                    policies,
                                    batch.getOrDefault(candidate, List.of()))
                            .orderable()) {
                        suggestedDate = candidate;
                        break;
                    }
                }
            }
            log.debug(
                    "Customer inventory checked: branchId={}, requestedDate={}, orderable={},"
                            + " suggestedDate={}, itemCount={}",
                    branchId,
                    request.serviceDate(),
                    requestedResult.orderable(),
                    suggestedDate,
                    requested.size());
            return new CustomerInventoryCheckResponse(
                    true,
                    request.serviceDate(),
                    requestedResult.orderable(),
                    suggestedDate,
                    !requestedResult.orderable() && suggestedDate == null,
                    requestedResult.items());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "check(Long,CustomerInventoryCheckRequest,boolean)");
        }
    }

    /**
     * Evaluates date.
     *
     * @param date the date
     * @param requested the requested
     * @param byProductId the by product id
     * @param policies the policies
     * @param dateAllocations the date allocations
     * @return the evaluate date result
     */
    private DateResult evaluateDate(
            LocalDate date,
            Map<Long, CustomerInventoryCheckRequest.Item> requested,
            Map<Long, BranchProduct> byProductId,
            Map<Long, BranchInventoryPolicy> policies,
            List<InventoryDailyAllocation> dateAllocations) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "evaluateDate(LocalDate,Map<Long,CustomerInventoryCheckRequest.Item>,Map<Long,BranchProduct>,Map<Long,BranchInventoryPolicy>,List<InventoryDailyAllocation>)");
        try {
            Map<Long, InventoryDailyAllocation> allocations =
                    dateAllocations.stream()
                            .collect(
                                    Collectors.toMap(
                                            allocation -> allocation.getBranchProduct().getId(),
                                            Function.identity()));
            List<CustomerInventoryCheckResponse.Item> results = new ArrayList<>();
            boolean allOrderable = true;
            for (Map.Entry<Long, CustomerInventoryCheckRequest.Item> entry : requested.entrySet()) {
                BranchProduct branchProduct = byProductId.get(entry.getKey());
                BranchInventoryPolicy policy = policies.get(branchProduct.getId());
                BigDecimal requestedQuantity =
                        requestedQuantity(branchProduct, entry.getValue(), policy);
                CustomerInventoryCheckResponse.Item result;
                if (!branchProduct.isAvailable()) {
                    result =
                            unavailable(
                                    branchProduct,
                                    policy,
                                    requestedQuantity,
                                    "This product is currently unavailable at the selected"
                                            + " branch.");
                } else if (policy == null) {
                    result =
                            unavailable(
                                    branchProduct,
                                    null,
                                    requestedQuantity,
                                    "Online inventory is not configured for this product.");
                } else if (!policy.isOnlineEnabled()) {
                    result =
                            unavailable(
                                    branchProduct,
                                    policy,
                                    requestedQuantity,
                                    "This product is not enabled for online ordering.");
                } else if (policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY) {
                    result =
                            unavailable(
                                    branchProduct,
                                    policy,
                                    requestedQuantity,
                                    "Please call the branch to confirm this large order.");
                } else if (date.isAfter(
                        LocalDate.now(inventoryClock).plusDays(policy.getBookingHorizonDays()))) {
                    result =
                            unavailable(
                                    branchProduct,
                                    policy,
                                    requestedQuantity,
                                    "This date is outside the product's booking window.");
                } else {
                    InventoryDailyAllocation allocation = allocations.get(branchProduct.getId());
                    if (allocation == null) {
                        result =
                                unavailable(
                                        branchProduct,
                                        policy,
                                        requestedQuantity,
                                        "Inventory has not been released for this date.");
                    } else {
                        InventoryAvailability availability =
                                availabilityService.calculate(allocation, policy);
                        boolean enough =
                                availability.orderable()
                                        && availability
                                                        .availableQuantity()
                                                        .compareTo(requestedQuantity)
                                                >= 0;
                        String reason =
                                enough
                                        ? null
                                        : availability.unavailableReason() != null
                                                ? availability.unavailableReason()
                                                : "Only "
                                                        + availability
                                                                .availableQuantity()
                                                                .stripTrailingZeros()
                                                                .toPlainString()
                                                        + " is available for this date.";
                        result =
                                new CustomerInventoryCheckResponse.Item(
                                        branchProduct.getProduct().getId(),
                                        branchProduct.getProduct().getName(),
                                        policy.getInventoryUnit(),
                                        requestedQuantity,
                                        availability.availableQuantity(),
                                        enough,
                                        reason);
                    }
                }
                results.add(result);
                allOrderable &= result.orderable();
            }
            return new DateResult(allOrderable, List.copyOf(results));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "evaluateDate(LocalDate,Map<Long,CustomerInventoryCheckRequest.Item>,Map<Long,BranchProduct>,Map<Long,BranchInventoryPolicy>,List<InventoryDailyAllocation>)");
        }
    }

    /**
     * Requesteds quantity.
     *
     * @param branchProduct the branch product
     * @param item the item
     * @param policy the policy
     * @return the requested quantity result
     */
    private BigDecimal requestedQuantity(
            BranchProduct branchProduct,
            CustomerInventoryCheckRequest.Item item,
            BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "requestedQuantity(BranchProduct,CustomerInventoryCheckRequest.Item,BranchInventoryPolicy)");
        try {
            if (branchProduct.getProduct().getSaleMode() == ProductSaleMode.WEIGHT) {
                if (item.weightGrams() == null) {
                    throw new IllegalArgumentException(
                            "Weight is required for " + branchProduct.getProduct().getName() + ".");
                }
                return policy == null
                        ? BigDecimal.valueOf(item.weightGrams())
                        : quantityService.normalizePositive(
                                BigDecimal.valueOf(item.weightGrams()),
                                policy.getInventoryUnit(),
                                "Requested inventory quantity");
            }
            if (item.quantity() == null) {
                throw new IllegalArgumentException(
                        "Quantity is required for " + branchProduct.getProduct().getName() + ".");
            }
            return policy == null
                    ? BigDecimal.valueOf(item.quantity())
                    : quantityService.normalizePositive(
                            BigDecimal.valueOf(item.quantity()),
                            policy.getInventoryUnit(),
                            "Requested inventory quantity");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "requestedQuantity(BranchProduct,CustomerInventoryCheckRequest.Item,BranchInventoryPolicy)");
        }
    }

    /**
     * Returns unavailable information for customer inventory availability.
     *
     * @param branchProduct the branch product supplied to this method
     * @param policy the policy supplied to this method
     * @param requestedQuantity the requested quantity supplied to this method
     * @param reason the reason supplied to this method
     * @return the {@code CustomerInventoryCheckResponse.Item} result
     */
    private CustomerInventoryCheckResponse.Item unavailable(
            BranchProduct branchProduct,
            BranchInventoryPolicy policy,
            BigDecimal requestedQuantity,
            String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "unavailable(BranchProduct,BranchInventoryPolicy,BigDecimal,String)");
        try {
            return new CustomerInventoryCheckResponse.Item(
                    branchProduct.getProduct().getId(),
                    branchProduct.getProduct().getName(),
                    policy == null ? null : policy.getInventoryUnit(),
                    requestedQuantity,
                    BigDecimal.ZERO,
                    false,
                    reason);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "unavailable(BranchProduct,BranchInventoryPolicy,BigDecimal,String)");
        }
    }

    /**
     * Normalizes items.
     *
     * @param items the items
     * @return the normalize items result
     */
    private Map<Long, CustomerInventoryCheckRequest.Item> normalizeItems(
            List<CustomerInventoryCheckRequest.Item> items) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "normalizeItems(List<CustomerInventoryCheckRequest.Item>)");
        try {
            Map<Long, CustomerInventoryCheckRequest.Item> normalized = new LinkedHashMap<>();
            for (CustomerInventoryCheckRequest.Item item : items) {
                if (normalized.put(item.productId(), item) != null) {
                    throw new IllegalArgumentException("Duplicate products are not allowed.");
                }
            }
            return normalized;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "normalizeItems(List<CustomerInventoryCheckRequest.Item>)");
        }
    }

    /**
     * Maximums search days.
     *
     * @param policies the policies
     * @return the maximum search days result
     */
    private int maximumSearchDays(Collection<BranchInventoryPolicy> policies) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class,
                        "maximumSearchDays(Collection<BranchInventoryPolicy>)");
        try {
            return policies.stream()
                    .filter(BranchInventoryPolicy::isOnlineEnabled)
                    .map(BranchInventoryPolicy::getBookingHorizonDays)
                    .filter(value -> value != null && value >= 0)
                    .min(Integer::compareTo)
                    .orElse(properties.getDefaultBookingHorizonDays());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "maximumSearchDays(Collection<BranchInventoryPolicy>)");
        }
    }

    /**
     * Requires active branch.
     *
     * @param branchId the branch id
     * @return the require active branch result
     */
    private Branch requireActiveBranch(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryAvailabilityService.class, "requireActiveBranch(Long)");
        try {
            return branchRepository
                    .findById(branchId)
                    .filter(Branch::isActive)
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "Selected branch is currently unavailable."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryAvailabilityService.class,
                    "requireActiveBranch(Long)");
        }
    }

    /**
     * Immutable date result data contract.
     *
     * @param orderable the orderable
     * @param items the items
     */
    private record DateResult(boolean orderable, List<CustomerInventoryCheckResponse.Item> items) {}
}
