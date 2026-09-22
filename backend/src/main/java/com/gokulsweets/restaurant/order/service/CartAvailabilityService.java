package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartAvailabilityService {
    private final EnhancementProperties features;
    private final InventoryProperties inventoryProperties;
    private final OrderValidationService validation;
    private final SmartOrderingRules rules;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryAvailabilityService inventoryAvailability;
    private final PickupSlotRepository slotRepository;
    private final BranchPickupSettingsRepository settingsRepository;
    private final Clock inventoryClock;

    @Transactional(readOnly = true)
    public Availability check(Long branchId, LocalDate startDate, int days, List<CreateOrderItemRequest> requested) {
        LocalDate today = LocalDate.now(inventoryClock);
        if (startDate.isBefore(today) || startDate.isAfter(today.plusDays(features.getFutureOrderingDays()))) {
            throw new IllegalArgumentException("Choose a date within the advance ordering window.");
        }
        List<ValidatedOrderItem> items = validation.validateCart(branchId, requested);
        List<Long> ids = items.stream().map(item -> item.branchProduct().getId()).toList();
        Map<Long, BranchInventoryPolicy> policies = policyRepository.findByBranchProductIdIn(ids).stream()
                .collect(Collectors.toMap(policy -> policy.getBranchProduct().getId(), Function.identity()));
        var settings = settingsRepository.findByBranchId(branchId).orElse(null);
        LocalDate lastDate = startDate.plusDays(days - 1L);
        LocalDate maximumDate = today.plusDays(features.getFutureOrderingDays());
        if (lastDate.isAfter(maximumDate)) lastDate = maximumDate;
        Map<StockKey, InventoryDailyAllocation> allocations = inventoryProperties.isEnforcementEnabled()
                ? allocationRepository.findByBranchProductIdInAndServiceDateBetween(ids, startDate, lastDate).stream()
                .collect(Collectors.toMap(a -> new StockKey(a.getBranchProduct().getId(), a.getServiceDate()), Function.identity()))
                : Map.of();
        Map<LocalDate, List<PickupSlot>> slotsByDate = slotRepository
                .findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(branchId, startDate, lastDate).stream()
                .collect(Collectors.groupingBy(PickupSlot::getSlotDate));
        List<DateAvailability> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            List<SlotAvailability> slots = new ArrayList<>();
            for (PickupSlot slot : slotsByDate.getOrDefault(date, List.of())) {
                String reason = rules.windowReason(slot, settings);
                if (reason == null && inventoryProperties.isEnforcementEnabled()) {
                    reason = inventoryReason(slot, items, policies, allocations);
                }
                boolean normal = reason == null && slot.getBookedCount() < slot.getCapacity();
                boolean priority = reason == null && slot.isPriorityEnabled()
                        && slot.getPriorityBookedCount() < slot.getPriorityCapacity();
                if (reason == null && !normal && !priority) reason = "This time is fully booked.";
                slots.add(new SlotAvailability(PickupSlotResponse.from(slot), normal, priority, reason));
            }
            dates.add(new DateAvailability(date, slots.stream().anyMatch(s -> s.normalAvailable() || s.priorityAvailable()), slots));
        }
        return new Availability("PICKUP", today, maximumDate, dates);
    }

    private String inventoryReason(PickupSlot slot, List<ValidatedOrderItem> items,
                                   Map<Long, BranchInventoryPolicy> policies, Map<StockKey, InventoryDailyAllocation> allocations) {
        for (ValidatedOrderItem item : items) {
            var policy = policies.get(item.branchProduct().getId());
            var allocation = allocations.get(new StockKey(item.branchProduct().getId(), slot.getSlotDate()));
            if (policy == null || !policy.isOnlineEnabled() || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY
                    || allocation == null || allocation.getInventoryUnit() != policy.getInventoryUnit()) {
                return item.product().getName() + " is not available for online ordering at this time.";
            }
            String timing = rules.preparationReason(slot, policy, allocation);
            if (timing != null) return timing;
            // Use the same held/committed/wasted stock calculation used by the locked final reservation.
            var stock = inventoryAvailability.calculate(allocation, policy);
            BigDecimal amount = BigDecimal.valueOf(item.saleMode() == ProductSaleMode.WEIGHT ? item.weightGrams() : item.quantity());
            if (!stock.orderable() || stock.availableQuantity().compareTo(amount) < 0) {
                return "The requested amount of " + item.product().getName() + " is unavailable. Try another date.";
            }
        }
        return null;
    }

    private record StockKey(Long branchProductId, LocalDate date) {}
    public record Availability(String fulfilmentType, LocalDate today, LocalDate maximumDate, List<DateAvailability> dates) {}
    public record DateAvailability(LocalDate date, boolean available, List<SlotAvailability> slots) {}
    public record SlotAvailability(PickupSlotResponse slot, boolean normalAvailable, boolean priorityAvailable, String reason) {}
}
