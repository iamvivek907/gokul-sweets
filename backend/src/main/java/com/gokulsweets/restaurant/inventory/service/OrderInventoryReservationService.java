package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** Coordinates order inventory reservation operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderInventoryReservationService {

    private final InventoryProperties properties;

    private final InventoryReservationRepository reservationRepository;

    private final InventoryDailyAllocationRepository allocationRepository;

    private final BranchInventoryPolicyRepository policyRepository;

    private final InventoryAvailabilityService availabilityService;

    private final InventoryQuantityService quantityService;

    private final InventoryLedgerService ledgerService;

    private final Clock inventoryClock;

    private final com.gokulsweets.restaurant.config.EnhancementProperties features;

    private final com.gokulsweets.restaurant.order.service.SmartOrderingRules smartOrderingRules;

    private final JdbcTemplate jdbc;

    /**
     * Synchronizes pending order.
     *
     * @param order the order
     * @param validatedOrder the validated order
     */
    @Transactional
    public void synchronizePendingOrder(Order order, ValidatedOrderData validatedOrder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "synchronizePendingOrder(Order,ValidatedOrderData)");
        try {
            synchronizePendingOrder(order, validatedOrder, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "synchronizePendingOrder(Order,ValidatedOrderData)");
        }
    }

    /**
     * Synchronizes pending order.
     *
     * @param order the order
     * @param validatedOrder the validated order
     * @param pickupChanged the pickup changed
     */
    @Transactional
    public void synchronizePendingOrder(
            Order order, ValidatedOrderData validatedOrder, boolean pickupChanged) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "synchronizePendingOrder(Order,ValidatedOrderData,boolean)");
        try {
            if (!properties.isEnforcementEnabled()) {
                return;
            }
            validatePendingOrder(order, validatedOrder, FulfillmentType.PICKUP);
            LocalDate serviceDate = validatedOrder.pickupSlot().getSlotDate();
            synchronizePendingInventory(
                    order,
                    validatedOrder,
                    serviceDate,
                    validatedOrder.pickupSlot().getStartTime(),
                    pickupChanged);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "synchronizePendingOrder(Order,ValidatedOrderData,boolean)");
        }
    }

    /**
     * Paid branch correction creates and confirms receiving stock in the same outer transaction.
     *
     * @param order the order
     * @param validated the validated
     * @param hadStock the had stock
     */
    @Transactional
    public void reserveTransferredOrder(
            Order order, ValidatedOrderData validated, boolean hadStock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "reserveTransferredOrder(Order,ValidatedOrderData,boolean)");
        try {
            if (!properties.isEnforcementEnabled() && !hadStock) return;
            var expiry = order.getReservationExpiresAt();
            try {
                order.setReservationExpiresAt(LocalDateTime.now(inventoryClock).plusMinutes(15));
                validatePendingOrder(order, validated, FulfillmentType.PICKUP);
                synchronizePendingInventory(
                        order,
                        validated,
                        validated.pickupSlot().getSlotDate(),
                        validated.pickupSlot().getStartTime(),
                        true);
            } finally {
                order.setReservationExpiresAt(expiry);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "reserveTransferredOrder(Order,ValidatedOrderData,boolean)");
        }
    }

    /**
     * Only a held window belonging to this order's branch can supply the inventory service date.
     *
     * @param order the order
     * @param validatedOrder the validated order
     */
    @Transactional
    public void synchronizePendingDeliveryOrder(Order order, ValidatedOrderData validatedOrder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "synchronizePendingDeliveryOrder(Order,ValidatedOrderData)");
        try {
            if (!properties.isEnforcementEnabled() || !features.isDeliveryRiderHolds())
                throw new IllegalStateException("Delivery inventory reservation is disabled.");
            validatePendingOrder(order, validatedOrder, FulfillmentType.DELIVERY);
            if (order.getDeliveryWindowId() == null || order.getDeliveryHoldKey() == null)
                throw new IllegalArgumentException("Delivery window and hold are required.");
            var windows =
                    jdbc.query(
                            """
                            SELECT w.service_date, w.starts_at FROM delivery_capacity_windows w
                            JOIN delivery_zones z ON z.id = w.zone_id
                            JOIN delivery_rider_holds h ON h.window_id = w.id
                            WHERE w.id = ? AND h.hold_key = ? AND z.branch_id = ?
                              AND h.state = 'HELD' AND h.expires_at > ? AND h.expires_at >= ?
                            FOR UPDATE OF h
                            """,
                            (rs, row) ->
                                    new WindowStart(
                                            rs.getDate(1).toLocalDate(),
                                            rs.getTime(2).toLocalTime()),
                            order.getDeliveryWindowId(),
                            order.getDeliveryHoldKey(),
                            order.getBranch().getId(),
                            java.sql.Timestamp.from(inventoryClock.instant()),
                            java.sql.Timestamp.from(
                                    order.getReservationExpiresAt()
                                            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                                            .toInstant()));
            if (windows.isEmpty())
                throw new InventoryConflictException(
                        "DELIVERY_HOLD_UNAVAILABLE",
                        "The selected delivery window is no longer reserved for this order.");
            WindowStart window = windows.getFirst();
            synchronizePendingInventory(
                    order, validatedOrder, window.date(), window.start(), false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "synchronizePendingDeliveryOrder(Order,ValidatedOrderData)");
        }
    }

    /**
     * Synchronizes pending inventory.
     *
     * @param order the order
     * @param validatedOrder the validated order
     * @param serviceDate the service date
     * @param serviceStart the service start
     * @param pickupChanged the pickup changed
     */
    private void synchronizePendingInventory(
            Order order,
            ValidatedOrderData validatedOrder,
            LocalDate serviceDate,
            LocalTime serviceStart,
            boolean pickupChanged) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "synchronizePendingInventory(Order,ValidatedOrderData,LocalDate,LocalTime,boolean)");
        try {
            LocalDateTime serviceAt = LocalDateTime.of(serviceDate, serviceStart);
            Map<Long, RequestedHold> requested = buildRequestedHolds(validatedOrder, serviceDate);
            List<InventoryReservation> existing =
                    reservationRepository.findByOrderNumberForUpdate(order.getOrderNumber());
            validateExistingReservations(existing);
            Map<Long, InventoryReservation> reusableByBranchProduct =
                    indexExistingReservations(existing);
            if (matchesCurrentHolds(existing, requested, order.getReservationExpiresAt())) {
                if ((features.isSmartAvailability() && pickupChanged)
                        || (order.getFulfillmentType() == FulfillmentType.DELIVERY
                                && serviceDate.isAfter(LocalDate.now(inventoryClock)))) {
                    // Reused holds still need current preparation and approved production promises.
                    var locked = lockAllRequiredAllocations(existing, requested);
                    for (RequestedHold hold : requested.values()) {
                        var allocation = requireLockedAllocation(locked, hold.key());
                        var policy = requirePolicy(hold.branchProductId());
                        validatePolicyForOrder(order, allocation, policy, serviceAt);
                        if (order.getFulfillmentType() == FulfillmentType.DELIVERY
                                && serviceDate.isAfter(LocalDate.now(inventoryClock))) {
                            // The current hold is already subtracted from available stock. Recheck
                            // total backing without releasing it or requiring extra unheld stock.
                            BigDecimal supply =
                                    policy.isReadyStockRequired()
                                            ? allocation
                                                    .getReadyQuantity()
                                                    .min(allocation.getApprovedQuantity())
                                            : allocation.getApprovedQuantity();
                            BigDecimal promised =
                                    allocation
                                            .getHeldQuantity()
                                            .add(allocation.getCommittedQuantity())
                                            .add(allocation.getSafetyBufferQuantity())
                                            .add(allocation.getWastedQuantity());
                            if (supply.compareTo(promised) < 0) {
                                throw new InventoryConflictException(
                                        "DELIVERY_PRODUCTION_SHORTFALL",
                                        "Approved production no longer covers the reserved delivery"
                                                + " quantity. Choose another time or pickup.");
                            }
                        }
                    }
                }
                return;
            }
            Map<AllocationKey, InventoryDailyAllocation> lockedAllocations =
                    lockAllRequiredAllocations(existing, requested);
            releaseActiveHolds(
                    existing, lockedAllocations, "Pending checkout inventory was recalculated.");
            for (RequestedHold hold : requested.values()) {
                InventoryDailyAllocation allocation =
                        requireLockedAllocation(lockedAllocations, hold.key());
                BranchInventoryPolicy policy = requirePolicy(hold.branchProductId());
                validatePolicyForOrder(order, allocation, policy, serviceAt);
                BigDecimal quantity =
                        quantityService.normalizePositive(
                                hold.quantity(),
                                policy.getInventoryUnit(),
                                "Requested inventory quantity");
                InventoryAvailability availability =
                        availabilityService.calculate(allocation, policy);
                if (!availability.orderable()
                        || availability.availableQuantity().compareTo(quantity) < 0) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("branchProductId", hold.branchProductId());
                    details.put("productName", hold.productName());
                    details.put("serviceDate", hold.key().serviceDate());
                    details.put("inventoryUnit", policy.getInventoryUnit());
                    details.put("requestedQuantity", quantity);
                    details.put("availableQuantity", availability.availableQuantity());
                    if (availability.expectedReadyAt() != null) {
                        details.put("expectedReadyAt", availability.expectedReadyAt());
                    }
                    throw new InventoryConflictException(
                            "INSUFFICIENT_INVENTORY",
                            availability.unavailableReason() == null
                                    ? "The requested quantity of "
                                            + hold.productName()
                                            + " is no longer available."
                                    : hold.productName() + ": " + availability.unavailableReason(),
                            details);
                }
                InventoryReservation reservation =
                        reusableByBranchProduct.get(hold.branchProductId());
                if (reservation == null) {
                    reservation = new InventoryReservation();
                    reservation.setReservationKey(
                            buildReservationKey(order.getOrderNumber(), hold.branchProductId()));
                }
                allocation.setHeldQuantity(allocation.getHeldQuantity().add(quantity));
                reservation.setAllocation(allocation);
                reservation.setOrderNumber(order.getOrderNumber());
                reservation.setQuantity(quantity);
                reservation.setStatus(InventoryReservationStatus.TEMPORARY_HOLD);
                reservation.setExpiresAt(order.getReservationExpiresAt());
                reservation.setConfirmedAt(null);
                reservation.setReleasedAt(null);
                reservation.setReleaseReason(null);
                InventoryReservation saved = reservationRepository.save(reservation);
                ledgerService.record(
                        allocation,
                        saved,
                        InventoryTransactionType.TEMPORARY_HOLD,
                        quantity.negate(),
                        order.getOrderNumber(),
                        saved.getReservationKey(),
                        "Pending checkout inventory reserved.",
                        null);
            }
            log.info(
                    "Pending-order inventory synchronized: orderNumber={}, serviceDate={},"
                            + " itemCount={}, expiresAt={}",
                    order.getOrderNumber(),
                    serviceDate,
                    requested.size(),
                    order.getReservationExpiresAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "synchronizePendingInventory(Order,ValidatedOrderData,LocalDate,LocalTime,boolean)");
        }
    }

    /**
     * Releases pending order holds.
     *
     * @param orderNumber the order number
     * @param reason the reason
     */
    @Transactional
    public void releasePendingOrderHolds(String orderNumber, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "releasePendingOrderHolds(String,String)");
        try {
            if (orderNumber == null || orderNumber.isBlank()) {
                return;
            }
            List<InventoryReservation> existing =
                    reservationRepository.findByOrderNumberForUpdate(orderNumber);
            if (existing.isEmpty()) {
                return;
            }
            Map<AllocationKey, InventoryDailyAllocation> lockedAllocations =
                    lockExistingActiveAllocations(existing);
            int released = releaseActiveHolds(existing, lockedAllocations, normalizeReason(reason));
            if (released > 0) {
                log.info(
                        "Pending-order inventory released: orderNumber={}, reservationCount={}",
                        orderNumber,
                        released);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "releasePendingOrderHolds(String,String)");
        }
    }

    /**
     * Builds requested holds.
     *
     * @param validatedOrder the validated order
     * @param serviceDate the service date
     * @return the build requested holds result
     */
    private Map<Long, RequestedHold> buildRequestedHolds(
            ValidatedOrderData validatedOrder, LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "buildRequestedHolds(ValidatedOrderData,LocalDate)");
        try {
            Map<Long, RequestedHold> requested = new TreeMap<>();
            for (ValidatedOrderItem item : validatedOrder.items()) {
                Long branchProductId = item.branchProduct().getId();
                BigDecimal quantity = quantityFor(item);
                RequestedHold previous =
                        requested.put(
                                branchProductId,
                                new RequestedHold(
                                        branchProductId,
                                        item.product().getName(),
                                        new AllocationKey(serviceDate, branchProductId),
                                        quantity));
                if (previous != null) {
                    throw new InventoryConflictException(
                            "DUPLICATE_VALIDATED_PRODUCT",
                            "The normalized order contains a duplicate product.");
                }
            }
            return requested;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "buildRequestedHolds(ValidatedOrderData,LocalDate)");
        }
    }

    /**
     * Quantity for.
     *
     * @param item the item
     * @return the quantity for result
     */
    private BigDecimal quantityFor(ValidatedOrderItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class, "quantityFor(ValidatedOrderItem)");
        try {
            if (item.saleMode() == ProductSaleMode.WEIGHT) {
                if (item.weightGrams() == null || item.weightGrams() <= 0) {
                    throw new InventoryConflictException(
                            "VALIDATED_WEIGHT_MISSING",
                            "Validated weight is missing for a weight-based product.");
                }
                return BigDecimal.valueOf(item.weightGrams());
            }
            if (item.quantity() <= 0) {
                throw new InventoryConflictException(
                        "VALIDATED_QUANTITY_MISSING",
                        "Validated quantity is missing for a unit-based product.");
            }
            return BigDecimal.valueOf(item.quantity());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "quantityFor(ValidatedOrderItem)");
        }
    }

    /**
     * Locks all required allocations.
     *
     * @param existing the existing
     * @param requested the requested
     * @return the lock all required allocations result
     */
    private Map<AllocationKey, InventoryDailyAllocation> lockAllRequiredAllocations(
            List<InventoryReservation> existing, Map<Long, RequestedHold> requested) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "lockAllRequiredAllocations(List<InventoryReservation>,Map<Long,RequestedHold>)");
        try {
            SortedSet<AllocationKey> keys = new TreeSet<>();
            existing.stream()
                    .filter(this::isActiveHold)
                    .map(this::allocationKey)
                    .forEach(keys::add);
            requested.values().stream().map(RequestedHold::key).forEach(keys::add);
            return lockAllocations(keys);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "lockAllRequiredAllocations(List<InventoryReservation>,Map<Long,RequestedHold>)");
        }
    }

    /**
     * Performs the lock existing active allocations operation for order inventory reservation
     * service.
     *
     * @param existing the existing
     * @return the lock existing active allocations result
     */
    private Map<AllocationKey, InventoryDailyAllocation> lockExistingActiveAllocations(
            List<InventoryReservation> existing) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "lockExistingActiveAllocations(List<InventoryReservation>)");
        try {
            SortedSet<AllocationKey> keys = new TreeSet<>();
            existing.stream()
                    .filter(this::isActiveHold)
                    .map(this::allocationKey)
                    .forEach(keys::add);
            return lockAllocations(keys);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "lockExistingActiveAllocations(List<InventoryReservation>)");
        }
    }

    /**
     * Locks allocations.
     *
     * @param keys the keys
     * @return the lock allocations result
     */
    private Map<AllocationKey, InventoryDailyAllocation> lockAllocations(
            Collection<AllocationKey> keys) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "lockAllocations(Collection<AllocationKey>)");
        try {
            Map<AllocationKey, InventoryDailyAllocation> locked = new HashMap<>();
            for (AllocationKey key : keys) {
                InventoryDailyAllocation allocation =
                        allocationRepository
                                .findForUpdate(key.branchProductId(), key.serviceDate())
                                .orElseThrow(
                                        () ->
                                                new InventoryNotFoundException(
                                                        "ALLOCATION_NOT_FOUND",
                                                        "No approved inventory exists for one or"
                                                                + " more products on the selected"
                                                                + " pickup date."));
                locked.put(key, allocation);
            }
            return locked;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "lockAllocations(Collection<AllocationKey>)");
        }
    }

    /**
     * Releases active holds.
     *
     * @param reservations the reservations
     * @param lockedAllocations the locked allocations
     * @param reason the reason
     * @return the release active holds result
     */
    private int releaseActiveHolds(
            List<InventoryReservation> reservations,
            Map<AllocationKey, InventoryDailyAllocation> lockedAllocations,
            String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "releaseActiveHolds(List<InventoryReservation>,Map<AllocationKey,InventoryDailyAllocation>,String)");
        try {
            int released = 0;
            for (InventoryReservation reservation : reservations) {
                if (!isActiveHold(reservation)) {
                    continue;
                }
                InventoryDailyAllocation allocation =
                        requireLockedAllocation(lockedAllocations, allocationKey(reservation));
                BigDecimal updatedHeld =
                        allocation.getHeldQuantity().subtract(reservation.getQuantity());
                if (updatedHeld.compareTo(BigDecimal.ZERO) < 0) {
                    throw new InventoryConflictException(
                            "INVENTORY_COUNTER_MISMATCH",
                            "Inventory reservation counters are inconsistent. Manual review is"
                                    + " required.");
                }
                allocation.setHeldQuantity(updatedHeld);
                reservation.setStatus(InventoryReservationStatus.RELEASED);
                reservation.setReleasedAt(LocalDateTime.now(inventoryClock));
                reservation.setReleaseReason(reason);
                ledgerService.record(
                        allocation,
                        reservation,
                        InventoryTransactionType.HOLD_RELEASED,
                        reservation.getQuantity(),
                        reservation.getOrderNumber(),
                        reservation.getReservationKey(),
                        reason,
                        null);
                released++;
            }
            return released;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "releaseActiveHolds(List<InventoryReservation>,Map<AllocationKey,InventoryDailyAllocation>,String)");
        }
    }

    /**
     * Matcheses current holds.
     *
     * @param existing the existing
     * @param requested the requested
     * @param expiresAt the expires at
     * @return the matches current holds result
     */
    private boolean matchesCurrentHolds(
            List<InventoryReservation> existing,
            Map<Long, RequestedHold> requested,
            LocalDateTime expiresAt) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "matchesCurrentHolds(List<InventoryReservation>,Map<Long,RequestedHold>,LocalDateTime)");
        try {
            List<InventoryReservation> active =
                    existing.stream().filter(this::isActiveHold).toList();
            if (active.size() != requested.size()) {
                return false;
            }
            for (InventoryReservation reservation : active) {
                Long branchProductId = reservation.getAllocation().getBranchProduct().getId();
                RequestedHold hold = requested.get(branchProductId);
                if (hold == null
                        || !reservation
                                .getAllocation()
                                .getServiceDate()
                                .equals(hold.key().serviceDate())
                        || reservation.getQuantity().compareTo(hold.quantity()) != 0
                        || !Objects.equals(reservation.getExpiresAt(), expiresAt)) {
                    return false;
                }
            }
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "matchesCurrentHolds(List<InventoryReservation>,Map<Long,RequestedHold>,LocalDateTime)");
        }
    }

    /**
     * Indexs existing reservations.
     *
     * @param existing the existing
     * @return the index existing reservations result
     */
    private Map<Long, InventoryReservation> indexExistingReservations(
            List<InventoryReservation> existing) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "indexExistingReservations(List<InventoryReservation>)");
        try {
            Map<Long, InventoryReservation> indexed = new HashMap<>();
            for (InventoryReservation reservation : existing) {
                Long branchProductId = reservation.getAllocation().getBranchProduct().getId();
                InventoryReservation duplicate = indexed.put(branchProductId, reservation);
                if (duplicate != null) {
                    throw new InventoryConflictException(
                            "DUPLICATE_ORDER_RESERVATION",
                            "Multiple inventory reservations exist for the same order product."
                                    + " Manual review is required.");
                }
            }
            return indexed;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "indexExistingReservations(List<InventoryReservation>)");
        }
    }

    /**
     * Performs the validate existing reservations operation for order inventory reservation
     * service.
     *
     * @param existing the existing
     */
    private void validateExistingReservations(List<InventoryReservation> existing) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "validateExistingReservations(List<InventoryReservation>)");
        try {
            boolean hasCommittedReservation =
                    existing.stream()
                            .anyMatch(
                                    reservation ->
                                            reservation.getStatus()
                                                            == InventoryReservationStatus.CONFIRMED
                                                    || reservation.getStatus()
                                                            == InventoryReservationStatus
                                                                    .FULFILLED);
            if (hasCommittedReservation) {
                throw new InventoryConflictException(
                        "INVENTORY_ALREADY_COMMITTED",
                        "This order's inventory is already committed and can no longer be edited.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "validateExistingReservations(List<InventoryReservation>)");
        }
    }

    /**
     * Validates policy for order.
     *
     * @param order the order
     * @param allocation the allocation
     * @param policy the policy
     * @param serviceAt the service at
     */
    private void validatePolicyForOrder(
            Order order,
            InventoryDailyAllocation allocation,
            BranchInventoryPolicy policy,
            LocalDateTime serviceAt) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "validatePolicyForOrder(Order,InventoryDailyAllocation,BranchInventoryPolicy,LocalDateTime)");
        try {
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY && !policy.isOnlineEnabled())
                throw new InventoryConflictException(
                        "DELIVERY_INVENTORY_OFFLINE",
                        "One or more products are not available for delivery online.");
            if (features.isSmartAvailability()
                    && order.getFulfillmentType() == FulfillmentType.PICKUP) {
                String reason =
                        smartOrderingRules.preparationReason(
                                order.getPickupSlot(), policy, allocation);
                if (reason != null)
                    throw new InventoryConflictException("PICKUP_NOT_READY", reason);
            }
            LocalDateTime now = LocalDateTime.now(inventoryClock);
            LocalDate serviceDate = serviceAt.toLocalDate();
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY
                    && serviceDate.isAfter(now.toLocalDate())) {
                if (!features.isPlannedDeliveryProduction()
                        || policy.getControlMode() != InventoryControlMode.DAILY_PRODUCTION
                        || (allocation.getStatus() != InventoryAllocationStatus.APPROVED
                                && allocation.getStatus() != InventoryAllocationStatus.READY)
                        || (policy.isReadyStockRequired()
                                && allocation.getStatus() != InventoryAllocationStatus.READY)
                        || allocation.getExpectedReadyAt() == null
                        || serviceAt.isBefore(allocation.getExpectedReadyAt())) {
                    throw new InventoryConflictException(
                            "DELIVERY_PRODUCTION_NOT_READY",
                            "Approved production is not ready before this delivery window. Choose"
                                    + " another time or pickup.");
                }
            }
            if (serviceDate.isAfter(now.toLocalDate().plusDays(policy.getBookingHorizonDays()))) {
                throw new InventoryConflictException(
                        "BOOKING_HORIZON_EXCEEDED",
                        "This product can only be ordered up to "
                                + policy.getBookingHorizonDays()
                                + " days in advance.");
            }
            if (serviceAt.isBefore(now.plusMinutes(policy.getProductionLeadMinutes()))) {
                throw new InventoryConflictException(
                        "PRODUCTION_LEAD_TIME_NOT_MET",
                        "This product needs at least "
                                + policy.getProductionLeadMinutes()
                                + " minutes of preparation time. Please choose a later service"
                                + " window.");
            }
            if (policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY) {
                throw new InventoryConflictException(
                        "SLOT_CAPACITY_NOT_CONFIGURED",
                        "Kitchen slot-capacity reservation is not enabled yet for this product.");
            }
            Long allocationBranchId = allocation.getBranchProduct().getBranch().getId();
            if (!allocationBranchId.equals(order.getBranch().getId())) {
                throw new InventoryConflictException(
                        "INVENTORY_BRANCH_MISMATCH",
                        "Inventory allocation does not belong to the order branch.");
            }
            if (allocation.getInventoryUnit() != policy.getInventoryUnit()) {
                throw new InventoryConflictException(
                        "INVENTORY_UNIT_MISMATCH",
                        "Inventory policy and allocation use different units.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "validatePolicyForOrder(Order,InventoryDailyAllocation,BranchInventoryPolicy,LocalDateTime)");
        }
    }

    /**
     * Validates pending order.
     *
     * @param order the order
     * @param validatedOrder the validated order
     * @param expectedType the expected type
     */
    private void validatePendingOrder(
            Order order, ValidatedOrderData validatedOrder, FulfillmentType expectedType) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "validatePendingOrder(Order,ValidatedOrderData,FulfillmentType)");
        try {
            if (order == null
                    || order.getOrderNumber() == null
                    || order.getOrderNumber().isBlank()
                    || order.getReservationExpiresAt() == null
                    || validatedOrder == null
                    || order.getFulfillmentType() != expectedType
                    || (expectedType == FulfillmentType.PICKUP
                            && validatedOrder.pickupSlot() == null)
                    || (expectedType == FulfillmentType.DELIVERY
                            && (validatedOrder.pickupSlot() != null
                                    || validatedOrder.pickupType() != null
                                    || order.getPickupSlot() != null
                                    || order.getPickupType() != null))
                    || validatedOrder.items() == null
                    || validatedOrder.items().isEmpty()) {
                throw new IllegalArgumentException(
                        "Complete pending order inventory data is required.");
            }
            if (!order.getReservationExpiresAt().isAfter(LocalDateTime.now(inventoryClock))) {
                throw new InventoryConflictException(
                        "CHECKOUT_RESERVATION_EXPIRED",
                        "Your checkout reservation expired. Please select a pickup time again.");
            }
            if (order.getBranch() == null
                    || validatedOrder.branch() == null
                    || !order.getBranch().getId().equals(validatedOrder.branch().getId())) {
                throw new InventoryConflictException(
                        "ORDER_INVENTORY_BRANCH_MISMATCH",
                        "Validated inventory does not belong to the order branch.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "validatePendingOrder(Order,ValidatedOrderData,FulfillmentType)");
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
                MethodTiming.start(OrderInventoryReservationService.class, "requirePolicy(Long)");
        try {
            return policyRepository
                    .findByBranchProductId(branchProductId)
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "INVENTORY_POLICY_NOT_FOUND",
                                            "Inventory is not configured for one or more selected"
                                                    + " products."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "requirePolicy(Long)");
        }
    }

    /**
     * Requires locked allocation.
     *
     * @param locked the locked
     * @param key the key
     * @return the require locked allocation result
     */
    private InventoryDailyAllocation requireLockedAllocation(
            Map<AllocationKey, InventoryDailyAllocation> locked, AllocationKey key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "requireLockedAllocation(Map<AllocationKey,InventoryDailyAllocation>,AllocationKey)");
        try {
            InventoryDailyAllocation allocation = locked.get(key);
            if (allocation == null) {
                throw new InventoryNotFoundException(
                        "ALLOCATION_NOT_FOUND", "Inventory allocation no longer exists.");
            }
            return allocation;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "requireLockedAllocation(Map<AllocationKey,InventoryDailyAllocation>,AllocationKey)");
        }
    }

    /**
     * Reports whether active hold.
     *
     * @param reservation the reservation
     * @return the is active hold result
     */
    private boolean isActiveHold(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "isActiveHold(InventoryReservation)");
        try {
            return reservation.getStatus() == InventoryReservationStatus.TEMPORARY_HOLD;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "isActiveHold(InventoryReservation)");
        }
    }

    /**
     * Allocations key.
     *
     * @param reservation the reservation
     * @return the allocation key result
     */
    private AllocationKey allocationKey(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class,
                        "allocationKey(InventoryReservation)");
        try {
            return new AllocationKey(
                    reservation.getAllocation().getServiceDate(),
                    reservation.getAllocation().getBranchProduct().getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "allocationKey(InventoryReservation)");
        }
    }

    /**
     * Builds reservation key.
     *
     * @param orderNumber the order number
     * @param branchProductId the branch product id
     * @return the build reservation key result
     */
    private String buildReservationKey(String orderNumber, Long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class, "buildReservationKey(String,Long)");
        try {
            String key = "order:" + orderNumber + ":branch-product:" + branchProductId;
            if (key.length() > 100) {
                throw new InventoryConflictException(
                        "INVENTORY_RESERVATION_KEY_TOO_LONG",
                        "Unable to create the inventory reservation key.");
            }
            return key;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "buildReservationKey(String,Long)");
        }
    }

    /**
     * Normalizes reason.
     *
     * @param reason the reason
     * @return the normalize reason result
     */
    private String normalizeReason(String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryReservationService.class, "normalizeReason(String)");
        try {
            if (reason == null || reason.isBlank()) {
                return "Pending checkout inventory released.";
            }
            return reason.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryReservationService.class,
                    "normalizeReason(String)");
        }
    }

    /**
     * Immutable window start data contract.
     *
     * @param date the date
     * @param start the start
     */
    private record WindowStart(LocalDate date, LocalTime start) {}

    /**
     * Immutable requested hold data contract.
     *
     * @param branchProductId the branch product id
     * @param productName the product name
     * @param key the key
     * @param quantity the quantity
     */
    private record RequestedHold(
            Long branchProductId, String productName, AllocationKey key, BigDecimal quantity) {}

    /**
     * Immutable allocation key data contract.
     *
     * @param serviceDate the service date
     * @param branchProductId the branch product id
     */
    private record AllocationKey(LocalDate serviceDate, Long branchProductId)
            implements Comparable<AllocationKey> {

        /**
         * Compares to.
         *
         * @param other the other
         * @return the compare to result
         */
        @Override
        public int compareTo(AllocationKey other) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            OrderInventoryReservationService.AllocationKey.class,
                            "compareTo(AllocationKey)");
            try {
                int dateComparison = serviceDate.compareTo(other.serviceDate);
                if (dateComparison != 0) {
                    return dateComparison;
                }
                return branchProductId.compareTo(other.branchProductId);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        OrderInventoryReservationService.AllocationKey.class,
                        "compareTo(AllocationKey)");
            }
        }
    }
}
