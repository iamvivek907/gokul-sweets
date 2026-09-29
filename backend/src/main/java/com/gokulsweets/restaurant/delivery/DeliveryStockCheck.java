package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
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

@Service
@RequiredArgsConstructor
public class DeliveryStockCheck {
    private final InventoryProperties inventory;
    private final OrderValidationService validation;
    private final BranchInventoryPolicyRepository policies;
    private final InventoryDailyAllocationRepository allocations;
    private final InventoryAvailabilityService availability;
    private final Clock inventoryClock;

    @Transactional(readOnly = true)
    public Check check(long branchId, LocalDate date, List<CreateOrderItemRequest> requested) {
        return check(branchId, date, requested, null);
    }

    /** A future rider window must fit every line's approved plan and preparation cutoff. */
    @Transactional(readOnly = true)
    public Check checkWindow(long branchId, LocalDate date, LocalTime startsAt,
                             List<CreateOrderItemRequest> requested) {
        if (startsAt == null) return new Check(false, "Choose a delivery window.");
        return check(branchId, date, requested, startsAt);
    }

    private Check check(long branchId, LocalDate date, List<CreateOrderItemRequest> requested,
                        LocalTime startsAt) {
        if (requested == null || requested.isEmpty() || requested.size() > 50)
            return new Check(false, "Add between 1 and 50 valid cart items.");
        if (!inventory.isEnforcementEnabled())
            return new Check(false, "Daily stock verification is not enabled.");
        var items = validation.validateCart(branchId, requested);
        var ids = items.stream().map(item -> item.branchProduct().getId()).toList();
        Map<Long, BranchInventoryPolicy> byId = policies.findByBranchProductIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getBranchProduct().getId(), Function.identity()));
        Map<Long, InventoryDailyAllocation> daily = allocations.findByBranchProductIdInAndServiceDate(ids, date).stream()
                .collect(Collectors.toMap(a -> a.getBranchProduct().getId(), Function.identity()));
        LocalDate today = LocalDate.now(inventoryClock);
        for (var item : items) {
            var id = item.branchProduct().getId();
            var policy = byId.get(id);
            var allocation = daily.get(id);
            InventoryUnit unit = item.saleMode() == ProductSaleMode.WEIGHT ? InventoryUnit.GRAM : InventoryUnit.PIECE;
            BigDecimal quantity = BigDecimal.valueOf(item.saleMode() == ProductSaleMode.WEIGHT
                    ? item.weightGrams() : item.quantity());
            if (policy == null || allocation == null || !policy.isOnlineEnabled()
                    || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY
                    || policy.getInventoryUnit() != unit || allocation.getInventoryUnit() != unit
                    || date.isAfter(today.plusDays(policy.getBookingHorizonDays())))
                return new Check(false, "One or more cart items lack a valid online allocation for this date.");
            var available = availability.calculate(allocation, policy);
            if (!available.orderable() || available.availableQuantity().compareTo(quantity) < 0)
                return new Check(false, "One or more cart items lack enough available stock for this date.");
            if (startsAt != null) {
                LocalDateTime windowStart = date.atTime(startsAt);
                if (policy.getControlMode() != InventoryControlMode.DAILY_PRODUCTION
                        || allocation.getExpectedReadyAt() == null
                        || windowStart.isBefore(allocation.getExpectedReadyAt())
                        || windowStart.isBefore(LocalDateTime.now(inventoryClock)
                        .plusMinutes(policy.getProductionLeadMinutes())))
                    return new Check(false, "Production is not approved and ready before this delivery window.");
            }
        }
        return new Check(true, null);
    }

    public record Check(boolean available, String reason) {}
}
