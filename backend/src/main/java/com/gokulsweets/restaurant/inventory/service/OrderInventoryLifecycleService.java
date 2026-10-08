package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Coordinates order inventory lifecycle operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderInventoryLifecycleService {

    private final InventoryReservationRepository reservationRepository;

    private final InventoryDailyAllocationRepository allocationRepository;

    private final InventoryLedgerService ledgerService;

    private final Clock inventoryClock;

    /**
     * Confirms order inventory.
     *
     * @param orderNumber the order number
     */
    @Transactional
    public void confirmOrderInventory(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class, "confirmOrderInventory(String)");
        try {
            confirmReservations(orderNumber, lockOrderReservations(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "confirmOrderInventory(String)");
        }
    }

    /** Used only after the order-locked branch transfer has cancelled the original commitments. */
    @Transactional
    public void confirmTransferredOrderInventory(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "confirmTransferredOrderInventory(String)");
        try {
            confirmReservations(
                    orderNumber,
                    lockOrderReservations(orderNumber).stream()
                            .filter(r -> r.getStatus() != InventoryReservationStatus.CANCELLED)
                            .toList());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "confirmTransferredOrderInventory(String)");
        }
    }

    /**
     * Confirms reservations.
     *
     * @param orderNumber the order number
     * @param reservations the reservations
     */
    private void confirmReservations(String orderNumber, List<InventoryReservation> reservations) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "confirmReservations(String,List<InventoryReservation>)");
        try {
            if (reservations.isEmpty()) return;
            Map<AllocationKey, InventoryDailyAllocation> allocations =
                    lockAllocations(reservations);
            for (InventoryReservation reservation : reservations) {
                if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED) {
                    continue;
                }
                if (reservation.getStatus() != InventoryReservationStatus.TEMPORARY_HOLD) {
                    throw invalidTransition(reservation, "confirmed");
                }
                if (reservation.getExpiresAt() != null
                        && !reservation.getExpiresAt().isAfter(LocalDateTime.now(inventoryClock))) {
                    throw new InventoryConflictException(
                            "INVENTORY_HOLD_EXPIRED",
                            "The order inventory hold expired before payment confirmation.");
                }
                InventoryDailyAllocation allocation = allocationFor(allocations, reservation);
                subtractHeld(allocation, reservation.getQuantity());
                allocation.setCommittedQuantity(
                        allocation.getCommittedQuantity().add(reservation.getQuantity()));
                reservation.setStatus(InventoryReservationStatus.CONFIRMED);
                reservation.setConfirmedAt(LocalDateTime.now(inventoryClock));
                reservation.setExpiresAt(null);
                ledgerService.record(
                        allocation,
                        reservation,
                        InventoryTransactionType.COMMITMENT_CONFIRMED,
                        reservation.getQuantity().negate(),
                        orderNumber,
                        reservation.getReservationKey(),
                        "Order inventory confirmed after payment.",
                        null);
            }
            log.info(
                    "Order inventory confirmed: orderNumber={}, reservationCount={}",
                    orderNumber,
                    reservations.size());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "confirmReservations(String,List<InventoryReservation>)");
        }
    }

    /**
     * Fulfils order inventory.
     *
     * @param orderNumber the order number
     * @param actor the actor
     */
    @Transactional
    public void fulfilOrderInventory(String orderNumber, String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "fulfilOrderInventory(String,String)");
        try {
            List<InventoryReservation> reservations =
                    lockOrderReservations(orderNumber).stream()
                            .filter(r -> r.getStatus() != InventoryReservationStatus.CANCELLED)
                            .toList();
            if (reservations.isEmpty()) return;
            Map<AllocationKey, InventoryDailyAllocation> allocations =
                    lockAllocations(reservations);
            for (InventoryReservation reservation : reservations) {
                if (reservation.getStatus() == InventoryReservationStatus.FULFILLED) {
                    continue;
                }
                if (reservation.getStatus() != InventoryReservationStatus.CONFIRMED) {
                    throw invalidTransition(reservation, "fulfilled");
                }
                InventoryDailyAllocation allocation = allocationFor(allocations, reservation);
                allocation.setFulfilledQuantity(
                        allocation.getFulfilledQuantity().add(reservation.getQuantity()));
                reservation.setStatus(InventoryReservationStatus.FULFILLED);
                reservation.setReleasedAt(LocalDateTime.now(inventoryClock));
                ledgerService.record(
                        allocation,
                        reservation,
                        InventoryTransactionType.FULFILLED,
                        reservation.getQuantity().negate(),
                        orderNumber,
                        reservation.getReservationKey(),
                        "Order fulfilled for pickup or delivery.",
                        actor);
            }
            log.info(
                    "Order inventory fulfilled: orderNumber={}, reservationCount={}",
                    orderNumber,
                    reservations.size());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "fulfilOrderInventory(String,String)");
        }
    }

    /**
     * Cancels order inventory.
     *
     * @param orderNumber the order number
     * @param reason the reason
     * @param actor the actor
     */
    @Transactional
    public void cancelOrderInventory(String orderNumber, String reason, String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "cancelOrderInventory(String,String,String)");
        try {
            List<InventoryReservation> reservations = lockOrderReservations(orderNumber);
            if (reservations.isEmpty()) return;
            Map<AllocationKey, InventoryDailyAllocation> allocations =
                    lockAllocations(reservations);
            String normalizedReason = normalizeReason(reason);
            for (InventoryReservation reservation : reservations) {
                if (reservation.getStatus() == InventoryReservationStatus.CANCELLED
                        || reservation.getStatus() == InventoryReservationStatus.RELEASED
                        || reservation.getStatus() == InventoryReservationStatus.EXPIRED) {
                    continue;
                }
                if (reservation.getStatus() == InventoryReservationStatus.FULFILLED) {
                    throw invalidTransition(reservation, "cancelled");
                }
                InventoryDailyAllocation allocation = allocationFor(allocations, reservation);
                InventoryTransactionType type;
                BigDecimal delta = reservation.getQuantity();
                if (reservation.getStatus() == InventoryReservationStatus.TEMPORARY_HOLD) {
                    subtractHeld(allocation, reservation.getQuantity());
                    type = InventoryTransactionType.HOLD_RELEASED;
                } else if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED) {
                    subtractCommitted(allocation, reservation.getQuantity());
                    type = InventoryTransactionType.COMMITMENT_CANCELLED;
                } else {
                    throw invalidTransition(reservation, "cancelled");
                }
                reservation.setStatus(InventoryReservationStatus.CANCELLED);
                reservation.setReleasedAt(LocalDateTime.now(inventoryClock));
                reservation.setReleaseReason(normalizedReason);
                reservation.setExpiresAt(null);
                ledgerService.record(
                        allocation,
                        reservation,
                        type,
                        delta,
                        orderNumber,
                        reservation.getReservationKey(),
                        normalizedReason,
                        actor);
            }
            log.info(
                    "Order inventory cancelled: orderNumber={}, reservationCount={}, actor={}",
                    orderNumber,
                    reservations.size(),
                    actor);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "cancelOrderInventory(String,String,String)");
        }
    }

    /**
     * Locks order reservations.
     *
     * @param orderNumber the order number
     * @return the lock order reservations result
     */
    private List<InventoryReservation> lockOrderReservations(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class, "lockOrderReservations(String)");
        try {
            if (orderNumber == null || orderNumber.isBlank()) {
                throw new IllegalArgumentException("Order number is required.");
            }
            return reservationRepository.findByOrderNumberForUpdate(orderNumber.trim());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "lockOrderReservations(String)");
        }
    }

    /**
     * Locks allocations.
     *
     * @param reservations the reservations
     * @return the lock allocations result
     */
    private Map<AllocationKey, InventoryDailyAllocation> lockAllocations(
            List<InventoryReservation> reservations) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "lockAllocations(List<InventoryReservation>)");
        try {
            SortedSet<AllocationKey> keys = new TreeSet<>();
            for (InventoryReservation reservation : reservations) {
                keys.add(keyFor(reservation));
            }
            Map<AllocationKey, InventoryDailyAllocation> result = new HashMap<>();
            for (AllocationKey key : keys) {
                InventoryDailyAllocation allocation =
                        allocationRepository
                                .findForUpdate(key.branchProductId(), key.serviceDate())
                                .orElseThrow(
                                        () ->
                                                new InventoryNotFoundException(
                                                        "ALLOCATION_NOT_FOUND",
                                                        "Inventory allocation no longer exists for"
                                                                + " this order."));
                result.put(key, allocation);
            }
            return result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "lockAllocations(List<InventoryReservation>)");
        }
    }

    /**
     * Allocations for.
     *
     * @param allocations the allocations
     * @param reservation the reservation
     * @return the allocation for result
     */
    private InventoryDailyAllocation allocationFor(
            Map<AllocationKey, InventoryDailyAllocation> allocations,
            InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "allocationFor(Map<AllocationKey,InventoryDailyAllocation>,InventoryReservation)");
        try {
            InventoryDailyAllocation allocation = allocations.get(keyFor(reservation));
            if (allocation == null) {
                throw new InventoryNotFoundException(
                        "ALLOCATION_NOT_FOUND",
                        "Inventory allocation no longer exists for this order.");
            }
            return allocation;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "allocationFor(Map<AllocationKey,InventoryDailyAllocation>,InventoryReservation)");
        }
    }

    /**
     * Key for.
     *
     * @param reservation the reservation
     * @return the key for result
     */
    private AllocationKey keyFor(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class, "keyFor(InventoryReservation)");
        try {
            return new AllocationKey(
                    reservation.getAllocation().getServiceDate(),
                    reservation.getAllocation().getBranchProduct().getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "keyFor(InventoryReservation)");
        }
    }

    /**
     * Subtracts held.
     *
     * @param allocation the allocation
     * @param quantity the quantity
     */
    private void subtractHeld(InventoryDailyAllocation allocation, BigDecimal quantity) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "subtractHeld(InventoryDailyAllocation,BigDecimal)");
        try {
            BigDecimal next = allocation.getHeldQuantity().subtract(quantity);
            if (next.signum() < 0) throw counterMismatch();
            allocation.setHeldQuantity(next);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "subtractHeld(InventoryDailyAllocation,BigDecimal)");
        }
    }

    /**
     * Subtracts committed.
     *
     * @param allocation the allocation
     * @param quantity the quantity
     */
    private void subtractCommitted(InventoryDailyAllocation allocation, BigDecimal quantity) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "subtractCommitted(InventoryDailyAllocation,BigDecimal)");
        try {
            BigDecimal next = allocation.getCommittedQuantity().subtract(quantity);
            if (next.signum() < 0) throw counterMismatch();
            allocation.setCommittedQuantity(next);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "subtractCommitted(InventoryDailyAllocation,BigDecimal)");
        }
    }

    /**
     * Counters mismatch.
     *
     * @return the counter mismatch result
     */
    private InventoryConflictException counterMismatch() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderInventoryLifecycleService.class, "counterMismatch()");
        try {
            return new InventoryConflictException(
                    "INVENTORY_COUNTER_MISMATCH",
                    "Inventory counters are inconsistent. Manual review is required.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "counterMismatch()");
        }
    }

    /**
     * Invalids transition.
     *
     * @param reservation the reservation
     * @param target the target
     * @return the invalid transition result
     */
    private InventoryConflictException invalidTransition(
            InventoryReservation reservation, String target) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderInventoryLifecycleService.class,
                        "invalidTransition(InventoryReservation,String)");
        try {
            return new InventoryConflictException(
                    "INVALID_INVENTORY_TRANSITION",
                    "Inventory reservation "
                            + reservation.getReservationKey()
                            + " cannot be "
                            + target
                            + " from "
                            + reservation.getStatus()
                            + ".");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "invalidTransition(InventoryReservation,String)");
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
                MethodTiming.start(OrderInventoryLifecycleService.class, "normalizeReason(String)");
        try {
            if (reason == null || reason.isBlank()) return "Order inventory cancelled.";
            return reason.trim().length() <= 300 ? reason.trim() : reason.trim().substring(0, 300);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderInventoryLifecycleService.class,
                    "normalizeReason(String)");
        }
    }

    /** Immutable allocation key data contract. */
    private record AllocationKey(java.time.LocalDate serviceDate, Long branchProductId)
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
                            OrderInventoryLifecycleService.AllocationKey.class,
                            "compareTo(AllocationKey)");
            try {
                int date = serviceDate.compareTo(other.serviceDate);
                return date != 0 ? date : branchProductId.compareTo(other.branchProductId);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        OrderInventoryLifecycleService.AllocationKey.class,
                        "compareTo(AllocationKey)");
            }
        }
    }
}
