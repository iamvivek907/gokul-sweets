package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;
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

/** Coordinates cart availability operations. */
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

    /**
     * Returns check information for cart availability.
     *
     * @param branchId the branch id supplied to this method
     * @param startDate the start date supplied to this method
     * @param days the days supplied to this method
     * @param requested the requested supplied to this method
     * @return the value of {@code check(branchId, startDate, days, requested, false)}
     */
    @Transactional(readOnly = true)
    public Availability check(
            Long branchId, LocalDate startDate, int days, List<CreateOrderItemRequest> requested) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CartAvailabilityService.class,
                        "check(Long,LocalDate,int,List<CreateOrderItemRequest>)");
        try {
            return check(branchId, startDate, days, requested, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityService.class,
                    "check(Long,LocalDate,int,List<CreateOrderItemRequest>)");
        }
    }

    /**
     * Menu discovery spans individual product horizons; order validation still checks every item.
     *
     * @param branchId the branch id
     * @param startDate the start date
     * @param days the days
     * @param requested the requested
     * @param menuPreview the menu preview
     * @return the operation result
     */
    @Transactional(readOnly = true)
    public Availability check(
            Long branchId,
            LocalDate startDate,
            int days,
            List<CreateOrderItemRequest> requested,
            boolean menuPreview) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CartAvailabilityService.class,
                        "check(Long,LocalDate,int,List<CreateOrderItemRequest>,boolean)");
        try {
            LocalDate today = LocalDate.now(inventoryClock);
            if (startDate.isBefore(today)
                    || startDate.isAfter(today.plusDays(features.getFutureOrderingDays()))) {
                throw new IllegalArgumentException(
                        "Choose a date within the advance ordering window.");
            }
            var validated = validation.validatePickupCartWithWindows(branchId, requested);
            List<ValidatedOrderItem> items = validated.items();
            List<Long> ids = items.stream().map(item -> item.branchProduct().getId()).toList();
            Map<Long, BranchInventoryPolicy> policies =
                    policyRepository.findByBranchProductIdIn(ids).stream()
                            .collect(
                                    Collectors.toMap(
                                            policy -> policy.getBranchProduct().getId(),
                                            Function.identity()));
            var settings = settingsRepository.findByBranchId(branchId).orElse(null);
            LocalDate lastDate = startDate.plusDays(days - 1L);
            LocalDate maximumDate = today.plusDays(features.getFutureOrderingDays());
            if (settings != null
                    && today.plusDays(settings.getAdvanceBookingDays()).isBefore(maximumDate))
                maximumDate = today.plusDays(settings.getAdvanceBookingDays());
            if (inventoryProperties.isEnforcementEnabled() && !menuPreview) {
                for (var policy : policies.values())
                    if (today.plusDays(policy.getBookingHorizonDays()).isBefore(maximumDate))
                        maximumDate = today.plusDays(policy.getBookingHorizonDays());
            }
            // Still explain a specifically requested out-of-policy date instead of returning an
            // empty, ambiguous response.
            if (lastDate.isAfter(maximumDate))
                lastDate = maximumDate.isBefore(startDate) ? startDate : maximumDate;
            Map<StockKey, InventoryDailyAllocation> allocations =
                    inventoryProperties.isEnforcementEnabled()
                            ? allocationRepository
                                    .findByBranchProductIdInAndServiceDateBetween(
                                            ids, startDate, lastDate)
                                    .stream()
                                    .collect(
                                            Collectors.toMap(
                                                    a ->
                                                            new StockKey(
                                                                    a.getBranchProduct().getId(),
                                                                    a.getServiceDate()),
                                                    Function.identity()))
                            : Map.of();
            Map<LocalDate, List<PickupSlot>> slotsByDate =
                    slotRepository
                            .findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                                    branchId, startDate, lastDate)
                            .stream()
                            .collect(Collectors.groupingBy(PickupSlot::getSlotDate));
            var serviceAt = validated.serviceAt();
            List<DateAvailability> dates = new ArrayList<>();
            for (LocalDate date = startDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
                List<SlotAvailability> slots = new ArrayList<>();
                List<ItemAvailability> stock = new ArrayList<>();
                for (ValidatedOrderItem item : items) {
                    stock.add(
                            stockForDate(
                                    item,
                                    date,
                                    policies.get(item.branchProduct().getId()),
                                    allocations.get(
                                            new StockKey(item.branchProduct().getId(), date)),
                                    today));
                }
                for (PickupSlot slot : slotsByDate.getOrDefault(date, List.of())) {
                    var service = serviceAt.apply(slot.getSlotDate().atTime(slot.getStartTime()));
                    String reason = rules.windowReason(slot, settings);
                    List<ItemAvailability> issues = new ArrayList<>();
                    for (int index = 0; index < items.size(); index++) {
                        var item = items.get(index);
                        var checked = stock.get(index);
                        var policy = policies.get(item.branchProduct().getId());
                        if (checked.available() && policy != null) {
                            var allocation =
                                    allocations.get(
                                            new StockKey(item.branchProduct().getId(), date));
                            String timing = rules.preparationReason(slot, policy, allocation);
                            if (timing != null)
                                checked =
                                        checked.unavailable(
                                                allocation != null
                                                                && allocation.getExpectedReadyAt()
                                                                        != null
                                                                && slot.getSlotDate()
                                                                        .atTime(slot.getStartTime())
                                                                        .isBefore(
                                                                                allocation
                                                                                        .getExpectedReadyAt())
                                                        ? "NOT_READY"
                                                        : "PREPARATION_TIME",
                                                timing);
                        }
                        var status = service.status(item.product().getId());
                        if (checked.available() && status != null && !status.available())
                            checked = checked.unavailable(status.code(), status.message());
                        if (!checked.available()) issues.add(checked);
                    }
                    String code = reason != null ? "PICKUP_WINDOW" : null;
                    if (reason == null && !issues.isEmpty()) {
                        code = issues.getFirst().code();
                        reason =
                                issues.getFirst().productName() + ": " + issues.getFirst().reason();
                    }
                    boolean normal = reason == null && slot.getBookedCount() < slot.getCapacity();
                    boolean priority =
                            reason == null
                                    && slot.isPriorityEnabled()
                                    && slot.getPriorityBookedCount() < slot.getPriorityCapacity();
                    if (reason == null && !normal && !priority) {
                        reason = "This time is fully booked.";
                        code = "SLOT_FULL";
                    }
                    slots.add(
                            new SlotAvailability(
                                    PickupSlotResponse.from(slot),
                                    normal,
                                    priority,
                                    reason,
                                    code,
                                    issues));
                }
                List<ItemAvailability> dateItems = summarizeDateItems(stock, slots);
                boolean available =
                        slots.stream().anyMatch(s -> s.normalAvailable() || s.priorityAvailable());
                String dateReason =
                        available
                                ? null
                                : slots.isEmpty()
                                        ? "No pickup times have been scheduled for this date."
                                        : dateItems.stream()
                                                .filter(i -> !i.available())
                                                .map(i -> i.productName() + ": " + i.reason())
                                                .findFirst()
                                                .orElse(
                                                        "No single pickup time can fulfil all"
                                                                + " items. Try another date.");
                LocalDate planDate = date;
                boolean plannedProduction =
                        features.isPlannedPickupProduction()
                                && inventoryProperties.isEnforcementEnabled()
                                && date.isAfter(today)
                                && available
                                && !items.isEmpty()
                                && items.stream()
                                        .allMatch(
                                                item -> {
                                                    var policy =
                                                            policies.get(
                                                                    item.branchProduct().getId());
                                                    var allocation =
                                                            allocations.get(
                                                                    new StockKey(
                                                                            item.branchProduct()
                                                                                    .getId(),
                                                                            planDate));
                                                    return policy != null
                                                            && policy.getControlMode()
                                                                    == InventoryControlMode
                                                                            .DAILY_PRODUCTION
                                                            && allocation != null
                                                            && (allocation.getStatus()
                                                                            == com.gokulsweets
                                                                                    .restaurant
                                                                                    .inventory.enums
                                                                                    .InventoryAllocationStatus
                                                                                    .APPROVED
                                                                    || allocation.getStatus()
                                                                            == com.gokulsweets
                                                                                    .restaurant
                                                                                    .inventory.enums
                                                                                    .InventoryAllocationStatus
                                                                                    .READY);
                                                });
                dates.add(
                        new DateAvailability(
                                date, available, slots, dateItems, dateReason, plannedProduction));
            }
            return new Availability("PICKUP", today, maximumDate, dates);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityService.class,
                    "check(Long,LocalDate,int,List<CreateOrderItemRequest>,boolean)");
        }
    }

    /**
     * Summarises dated item availability using one index of issues in slots with capacity. Stock
     * failures and the first applicable slot reason retain their original precedence.
     *
     * @param stock authoritative dated stock decisions in request order
     * @param slots current slot decisions in server order
     * @return item decisions in the original request order
     */
    static List<ItemAvailability> summarizeDateItems(
            List<ItemAvailability> stock, List<SlotAvailability> slots) {
        long started =
                MethodTiming.start(
                        CartAvailabilityService.class,
                        "summarizeDateItems(List<ItemAvailability>,List<SlotAvailability>)");
        try {
            List<ItemAvailability> dateItems = new ArrayList<>();
            var inWindow = slots.stream().filter(s -> !"PICKUP_WINDOW".equals(s.code())).toList();
            var openSlots =
                    inWindow.stream()
                            .filter(
                                    s ->
                                            s.slot().remainingCapacity() > 0
                                                    || (s.slot().priorityEnabled()
                                                            && s.slot().priorityRemainingCapacity()
                                                                    > 0))
                            .toList();
            // Index each open slot once instead of scanning every issue for every product.
            // Keep first-issue order identical to the original slot traversal.
            Map<Long, Integer> blockedSlots = new HashMap<>();
            Map<Long, ItemAvailability> firstIssues = new HashMap<>();
            for (SlotAvailability openSlot : openSlots) {
                Set<Long> seen = new HashSet<>();
                for (ItemAvailability issue : openSlot.issues()) {
                    firstIssues.putIfAbsent(issue.productId(), issue);
                    if (seen.add(issue.productId()))
                        blockedSlots.merge(issue.productId(), 1, Integer::sum);
                }
            }
            for (ItemAvailability item : stock) {
                if (!item.available()) {
                    dateItems.add(item);
                    continue;
                }
                boolean fits = blockedSlots.getOrDefault(item.productId(), 0) < openSlots.size();
                if (fits) dateItems.add(item);
                else
                    dateItems.add(
                            firstIssues.getOrDefault(
                                    item.productId(),
                                    inWindow.isEmpty()
                                            ? item.unavailable(
                                                    "NO_SLOTS",
                                                    "No pickup times are open for this date. Choose"
                                                            + " another date.")
                                            : item.unavailable(
                                                    "SLOT_FULL",
                                                    "All pickup times are fully booked. Choose"
                                                            + " another date.")));
            }
            return dateItems;
        } finally {
            MethodTiming.finish(
                    started,
                    CartAvailabilityService.class,
                    "summarizeDateItems(List<ItemAvailability>,List<SlotAvailability>)");
        }
    }

    /**
     * Stocks for date.
     *
     * @param item the item
     * @param date the date
     * @param policy the policy
     * @param allocation the allocation
     * @param today the today
     * @return the stock for date result
     */
    private ItemAvailability stockForDate(
            ValidatedOrderItem item,
            LocalDate date,
            BranchInventoryPolicy policy,
            InventoryDailyAllocation allocation,
            LocalDate today) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CartAvailabilityService.class,
                        "stockForDate(ValidatedOrderItem,LocalDate,BranchInventoryPolicy,InventoryDailyAllocation,LocalDate)");
        try {
            BigDecimal amount =
                    BigDecimal.valueOf(
                            item.saleMode() == ProductSaleMode.WEIGHT
                                    ? item.weightGrams()
                                    : item.quantity());
            var result =
                    new ItemAvailability(
                            item.product().getId(),
                            item.product().getName(),
                            item.saleMode() == ProductSaleMode.WEIGHT ? "GRAM" : "PIECE",
                            amount,
                            null,
                            true,
                            null,
                            null,
                            allocation == null ? null : allocation.getExpectedReadyAt());
            if (!inventoryProperties.isEnforcementEnabled()) return result;
            if (policy == null
                    || !policy.isOnlineEnabled()
                    || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY)
                return result.unavailable(
                        "ONLINE_DISABLED",
                        "Not offered online for this pickup. Choose another item.");
            if (date.isAfter(today.plusDays(policy.getBookingHorizonDays())))
                return result.unavailable(
                        "PRODUCT_HORIZON",
                        "Can only be ordered up to "
                                + policy.getBookingHorizonDays()
                                + " days ahead.");
            if (allocation == null)
                return result.unavailable(
                        "NO_ALLOCATION",
                        "Not yet available to book for this date. Try another date.");
            if (allocation.getInventoryUnit() != policy.getInventoryUnit())
                return result.unavailable(
                        "POLICY_MISMATCH",
                        "Cannot currently be booked. Please contact the branch.");
            var stock = inventoryAvailability.calculate(allocation, policy);
            result =
                    new ItemAvailability(
                            result.productId(),
                            result.productName(),
                            result.unit(),
                            amount,
                            stock.availableQuantity(),
                            true,
                            null,
                            null,
                            result.expectedReadyAt());
            if (!stock.orderable()) {
                return switch (allocation.getStatus()) {
                    case DRAFT ->
                            result.unavailable(
                                    "AWAITING_APPROVAL",
                                    "Not yet available to book for this date.");
                    case DELAYED ->
                            result.unavailable(
                                    "DELAYED",
                                    "Preparation is delayed. Choose another date or contact the"
                                            + " branch.");
                    case CLOSED, UNAVAILABLE ->
                            result.unavailable(
                                    "BOOKING_CLOSED", "Booking is closed for this date.");
                    default ->
                            policy.isReadyStockRequired()
                                            && (allocation.getReadyQuantity().signum() == 0
                                                    || allocation.getStatus()
                                                            != com.gokulsweets.restaurant.inventory
                                                                    .enums.InventoryAllocationStatus
                                                                    .READY)
                                    ? result.unavailable(
                                            "READY_STOCK_REQUIRED",
                                            "Not ready for online sale yet. Try another date or"
                                                    + " item.")
                                    : result.unavailable(
                                            "SOLD_OUT",
                                            "No online quantity remains for this date.");
                };
            }
            return stock.availableQuantity().compareTo(amount) < 0
                    ? result.unavailable(
                            "QUANTITY_TOO_LARGE", "Choose a smaller quantity or another date.")
                    : result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityService.class,
                    "stockForDate(ValidatedOrderItem,LocalDate,BranchInventoryPolicy,InventoryDailyAllocation,LocalDate)");
        }
    }

    /**
     * Immutable stock key data contract.
     *
     * @param branchProductId the branch product id
     * @param date the date
     */
    private record StockKey(Long branchProductId, LocalDate date) {}

    /**
     * Immutable availability data contract.
     *
     * @param fulfilmentType the fulfilment type
     * @param today the today
     * @param maximumDate the maximum date
     * @param dates the dates
     */
    public record Availability(
            String fulfilmentType,
            LocalDate today,
            LocalDate maximumDate,
            List<DateAvailability> dates) {}

    /**
     * Immutable item availability data contract.
     *
     * @param productId the product id
     * @param productName the product name
     * @param unit the unit
     * @param requestedQuantity the requested quantity
     * @param availableQuantity the available quantity
     * @param available the available
     * @param code the code
     * @param reason the reason
     * @param expectedReadyAt the expected ready at
     */
    public record ItemAvailability(
            Long productId,
            String productName,
            String unit,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity,
            boolean available,
            String code,
            String reason,
            LocalDateTime expectedReadyAt) {

        /**
         * Returns unavailable information for item availability.
         *
         * @param code the code supplied to this method
         * @param reason the reason supplied to this method
         * @return the {@code ItemAvailability} result
         */
        ItemAvailability unavailable(String code, String reason) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            CartAvailabilityService.ItemAvailability.class,
                            "unavailable(String,String)");
            try {
                return new ItemAvailability(
                        productId,
                        productName,
                        unit,
                        requestedQuantity,
                        availableQuantity,
                        false,
                        code,
                        reason,
                        expectedReadyAt);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        CartAvailabilityService.ItemAvailability.class,
                        "unavailable(String,String)");
            }
        }
    }

    /**
     * Immutable date availability data contract.
     *
     * @param date the date
     * @param available the available
     * @param slots the slots
     * @param items the items
     * @param reason the reason
     * @param plannedProduction the planned production
     */
    public record DateAvailability(
            LocalDate date,
            boolean available,
            List<SlotAvailability> slots,
            List<ItemAvailability> items,
            String reason,
            boolean plannedProduction) {}

    /**
     * Immutable slot availability data contract.
     *
     * @param slot the slot
     * @param normalAvailable the normal available
     * @param priorityAvailable the priority available
     * @param reason the reason
     * @param code the code
     * @param issues the issues
     */
    public record SlotAvailability(
            PickupSlotResponse slot,
            boolean normalAvailable,
            boolean priorityAvailable,
            String reason,
            String code,
            List<ItemAvailability> issues) {}
}
