package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Backend delivery stock check contract and implementation. */
@Service
@RequiredArgsConstructor
public class DeliveryStockCheck {

    private final InventoryProperties inventory;

    private final OrderValidationService validation;

    private final BranchInventoryPolicyRepository policies;

    private final InventoryDailyAllocationRepository allocations;

    private final InventoryAvailabilityService availability;

    private final Clock inventoryClock;

    /**
     * Returns check information for delivery stock check.
     *
     * @param branchId the branch id supplied to this method
     * @param date the date supplied to this method
     * @param requested the requested supplied to this method
     * @return the value of {@code check(branchId, date, requested, null)}
     */
    @Transactional(readOnly = true)
    public Check check(long branchId, LocalDate date, List<CreateOrderItemRequest> requested) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryStockCheck.class,
                        "check(long,LocalDate,List<CreateOrderItemRequest>)");
        try {
            return check(branchId, date, requested, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryStockCheck.class,
                    "check(long,LocalDate,List<CreateOrderItemRequest>)");
        }
    }

    /**
     * A future rider window must fit every line's approved plan and preparation cutoff.
     *
     * @param branchId the branch id
     * @param date the date
     * @param startsAt the starts at
     * @param requested the requested
     * @return the operation result
     */
    @Transactional(readOnly = true)
    public Check checkWindow(
            long branchId,
            LocalDate date,
            LocalTime startsAt,
            List<CreateOrderItemRequest> requested) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryStockCheck.class,
                        "checkWindow(long,LocalDate,LocalTime,List<CreateOrderItemRequest>)");
        try {
            if (startsAt == null) return new Check(false, "Choose a delivery window.");
            return check(branchId, date, requested, startsAt);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryStockCheck.class,
                    "checkWindow(long,LocalDate,LocalTime,List<CreateOrderItemRequest>)");
        }
    }

    /**
     * Returns check information for delivery stock check.
     *
     * @param branchId the branch id supplied to this method
     * @param date the date supplied to this method
     * @param requested the requested supplied to this method
     * @param startsAt the starts at supplied to this method
     * @return the {@code Check} result
     */
    private Check check(
            long branchId,
            LocalDate date,
            List<CreateOrderItemRequest> requested,
            LocalTime startsAt) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryStockCheck.class,
                        "check(long,LocalDate,List<CreateOrderItemRequest>,LocalTime)");
        try {
            if (requested == null || requested.isEmpty() || requested.size() > 50)
                return new Check(false, "Add between 1 and 50 valid cart items.");
            if (!inventory.isEnforcementEnabled())
                return new Check(false, "Daily stock verification is not enabled.");
            var items = validation.validateCart(branchId, requested);
            var ids = items.stream().map(item -> item.branchProduct().getId()).toList();
            Map<Long, BranchInventoryPolicy> byId =
                    policies.findByBranchProductIdIn(ids).stream()
                            .collect(
                                    Collectors.toMap(
                                            p -> p.getBranchProduct().getId(),
                                            Function.identity()));
            Map<Long, InventoryDailyAllocation> daily =
                    allocations.findByBranchProductIdInAndServiceDate(ids, date).stream()
                            .collect(
                                    Collectors.toMap(
                                            a -> a.getBranchProduct().getId(),
                                            Function.identity()));
            LocalDate today = LocalDate.now(inventoryClock);
            for (var item : items) {
                var id = item.branchProduct().getId();
                var policy = byId.get(id);
                var allocation = daily.get(id);
                InventoryUnit unit =
                        item.saleMode() == ProductSaleMode.WEIGHT
                                ? InventoryUnit.GRAM
                                : InventoryUnit.PIECE;
                BigDecimal quantity =
                        BigDecimal.valueOf(
                                item.saleMode() == ProductSaleMode.WEIGHT
                                        ? item.weightGrams()
                                        : item.quantity());
                if (policy == null
                        || allocation == null
                        || !policy.isOnlineEnabled()
                        || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY
                        || policy.getInventoryUnit() != unit
                        || allocation.getInventoryUnit() != unit
                        || date.isAfter(today.plusDays(policy.getBookingHorizonDays())))
                    return new Check(
                            false,
                            "One or more cart items lack a valid online allocation for this date.");
                var available = availability.calculate(allocation, policy);
                if (!available.orderable() || available.availableQuantity().compareTo(quantity) < 0)
                    return new Check(
                            false,
                            "One or more cart items lack enough available stock for this date.");
                if (startsAt != null) {
                    LocalDateTime windowStart = date.atTime(startsAt);
                    if (policy.getControlMode() != InventoryControlMode.DAILY_PRODUCTION
                            || allocation.getExpectedReadyAt() == null
                            || windowStart.isBefore(allocation.getExpectedReadyAt())
                            || windowStart.isBefore(
                                    LocalDateTime.now(inventoryClock)
                                            .plusMinutes(policy.getProductionLeadMinutes())))
                        return new Check(
                                false,
                                "Production is not approved and ready before this delivery"
                                        + " window.");
                }
            }
            return new Check(true, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryStockCheck.class,
                    "check(long,LocalDate,List<CreateOrderItemRequest>,LocalTime)");
        }
    }

    /**
     * Immutable check data contract.
     *
     * @param available the available
     * @param reason the reason
     */
    public record Check(boolean available, String reason) {}
}
