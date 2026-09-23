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
import java.time.LocalDateTime;
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
        if (settings != null && today.plusDays(settings.getAdvanceBookingDays()).isBefore(maximumDate))
            maximumDate = today.plusDays(settings.getAdvanceBookingDays());
        if (inventoryProperties.isEnforcementEnabled()) {
            for (var policy : policies.values()) if (today.plusDays(policy.getBookingHorizonDays()).isBefore(maximumDate))
                maximumDate = today.plusDays(policy.getBookingHorizonDays());
        }
        // Still explain a specifically requested out-of-policy date instead of returning an empty, ambiguous response.
        if (lastDate.isAfter(maximumDate)) lastDate = maximumDate.isBefore(startDate) ? startDate : maximumDate;
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
            List<ItemAvailability> stock = new ArrayList<>();
            for (ValidatedOrderItem item : items) {
                stock.add(stockForDate(item, date, policies.get(item.branchProduct().getId()),
                        allocations.get(new StockKey(item.branchProduct().getId(), date)), today));
            }
            for (PickupSlot slot : slotsByDate.getOrDefault(date, List.of())) {
                String reason = rules.windowReason(slot, settings);
                List<ItemAvailability> issues = new ArrayList<>();
                for (int index = 0; index < items.size(); index++) {
                    var item = items.get(index);
                    var checked = stock.get(index);
                    var policy = policies.get(item.branchProduct().getId());
                    if (checked.available() && policy != null) {
                        var allocation = allocations.get(new StockKey(item.branchProduct().getId(), date));
                        String timing = rules.preparationReason(slot, policy, allocation);
                        if (timing != null) checked = checked.unavailable(
                                allocation != null && allocation.getExpectedReadyAt() != null
                                        && slot.getSlotDate().atTime(slot.getStartTime()).isBefore(allocation.getExpectedReadyAt())
                                        ? "NOT_READY" : "PREPARATION_TIME", timing);
                    }
                    if (!checked.available()) issues.add(checked);
                }
                String code = reason != null ? "PICKUP_WINDOW" : null;
                if (reason == null && !issues.isEmpty()) {
                    code = issues.getFirst().code();
                    reason = issues.getFirst().productName() + ": " + issues.getFirst().reason();
                }
                boolean normal = reason == null && slot.getBookedCount() < slot.getCapacity();
                boolean priority = reason == null && slot.isPriorityEnabled()
                        && slot.getPriorityBookedCount() < slot.getPriorityCapacity();
                if (reason == null && !normal && !priority) {reason = "This time is fully booked."; code = "SLOT_FULL";}
                slots.add(new SlotAvailability(PickupSlotResponse.from(slot), normal, priority, reason, code, issues));
            }
            List<ItemAvailability> dateItems = new ArrayList<>();
            var inWindow = slots.stream().filter(s -> !"PICKUP_WINDOW".equals(s.code())).toList();
            var openSlots = inWindow.stream().filter(s ->
                    s.slot().remainingCapacity() > 0 || (s.slot().priorityEnabled() && s.slot().priorityRemainingCapacity() > 0)).toList();
            for (ItemAvailability item : stock) {
                if (!item.available()) {dateItems.add(item); continue;}
                boolean fits = openSlots.stream().anyMatch(s -> s.issues().stream().noneMatch(i -> i.productId().equals(item.productId())));
                if (fits) dateItems.add(item);
                else dateItems.add(openSlots.stream().flatMap(s -> s.issues().stream())
                        .filter(i -> i.productId().equals(item.productId())).findFirst()
                        .orElse(inWindow.isEmpty()
                                ? item.unavailable("NO_SLOTS", "No pickup times are open for this date. Choose another date.")
                                : item.unavailable("SLOT_FULL", "All pickup times are fully booked. Choose another date.")));
            }
            boolean available = slots.stream().anyMatch(s -> s.normalAvailable() || s.priorityAvailable());
            String dateReason = available ? null : slots.isEmpty() ? "No pickup times have been scheduled for this date."
                    : dateItems.stream().filter(i -> !i.available()).map(i -> i.productName() + ": " + i.reason())
                    .findFirst().orElse("No single pickup time can fulfil all items. Try another date.");
            dates.add(new DateAvailability(date, available, slots, dateItems, dateReason));
        }
        return new Availability("PICKUP", today, maximumDate, dates);
    }

    private ItemAvailability stockForDate(ValidatedOrderItem item, LocalDate date, BranchInventoryPolicy policy,
                                         InventoryDailyAllocation allocation, LocalDate today) {
        BigDecimal amount = BigDecimal.valueOf(item.saleMode() == ProductSaleMode.WEIGHT ? item.weightGrams() : item.quantity());
        var result = new ItemAvailability(item.product().getId(), item.product().getName(),
                item.saleMode() == ProductSaleMode.WEIGHT ? "GRAM" : "PIECE", amount, null, true, null, null,
                allocation == null ? null : allocation.getExpectedReadyAt());
        if (!inventoryProperties.isEnforcementEnabled()) return result;
        if (policy == null || !policy.isOnlineEnabled() || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY)
            return result.unavailable("ONLINE_DISABLED", "Not offered online for this pickup. Choose another item.");
        if (date.isAfter(today.plusDays(policy.getBookingHorizonDays())))
            return result.unavailable("PRODUCT_HORIZON", "Can only be ordered up to " + policy.getBookingHorizonDays() + " days ahead.");
        if (allocation == null) return result.unavailable("NO_ALLOCATION", "Not yet available to book for this date. Try another date.");
        if (allocation.getInventoryUnit() != policy.getInventoryUnit())
            return result.unavailable("POLICY_MISMATCH", "Cannot currently be booked. Please contact the branch.");
        var stock = inventoryAvailability.calculate(allocation, policy);
        result = new ItemAvailability(result.productId(), result.productName(), result.unit(), amount,
                stock.availableQuantity(), true, null, null, result.expectedReadyAt());
        if (!stock.orderable()) {
            return switch (allocation.getStatus()) {
                case DRAFT -> result.unavailable("AWAITING_APPROVAL", "Not yet available to book for this date.");
                case DELAYED -> result.unavailable("DELAYED", "Preparation is delayed. Choose another date or contact the branch.");
                case CLOSED, UNAVAILABLE -> result.unavailable("BOOKING_CLOSED", "Booking is closed for this date.");
                default -> policy.isReadyStockRequired() && (allocation.getReadyQuantity().signum() == 0
                        || allocation.getStatus() != com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus.READY)
                        ? result.unavailable("READY_STOCK_REQUIRED", "Not ready for online sale yet. Try another date or item.")
                        : result.unavailable("SOLD_OUT", "No online quantity remains for this date.");
            };
        }
        return stock.availableQuantity().compareTo(amount) < 0
                ? result.unavailable("QUANTITY_TOO_LARGE", "Choose a smaller quantity or another date.") : result;
    }

    private record StockKey(Long branchProductId, LocalDate date) {}
    public record Availability(String fulfilmentType, LocalDate today, LocalDate maximumDate, List<DateAvailability> dates) {}
    public record ItemAvailability(Long productId, String productName, String unit, BigDecimal requestedQuantity,
                                   BigDecimal availableQuantity, boolean available, String code, String reason,
                                   LocalDateTime expectedReadyAt) {
        ItemAvailability unavailable(String code, String reason) {
            return new ItemAvailability(productId, productName, unit, requestedQuantity, availableQuantity, false, code, reason, expectedReadyAt);
        }
    }
    public record DateAvailability(LocalDate date, boolean available, List<SlotAvailability> slots, List<ItemAvailability> items, String reason) {}
    public record SlotAvailability(PickupSlotResponse slot, boolean normalAvailable, boolean priorityAvailable, String reason,
                                   String code, List<ItemAvailability> issues) {}
}
