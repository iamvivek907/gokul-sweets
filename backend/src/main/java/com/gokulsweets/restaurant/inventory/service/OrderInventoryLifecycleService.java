package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderInventoryLifecycleService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryLedgerService ledgerService;
    private final Clock inventoryClock;

    /* Future payment-success hook. Safe and idempotent. */
    @Transactional
    public void confirmOrderInventory(String orderNumber) {
        List<InventoryReservation> reservations = lockOrderReservations(orderNumber);
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
                        "The order inventory hold expired before payment confirmation."
                );
            }

            InventoryDailyAllocation allocation = allocationFor(allocations, reservation);
            subtractHeld(allocation, reservation.getQuantity());
            allocation.setCommittedQuantity(
                    allocation.getCommittedQuantity().add(reservation.getQuantity())
            );
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
                    null
            );
        }

        log.info("Order inventory confirmed: orderNumber={}, reservationCount={}",
                orderNumber, reservations.size());
    }

    @Transactional
    public void fulfilOrderInventory(String orderNumber, String actor) {
        List<InventoryReservation> reservations = lockOrderReservations(orderNumber);
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
                    allocation.getFulfilledQuantity().add(reservation.getQuantity())
            );
            reservation.setStatus(InventoryReservationStatus.FULFILLED);
            reservation.setReleasedAt(LocalDateTime.now(inventoryClock));

            ledgerService.record(
                    allocation,
                    reservation,
                    InventoryTransactionType.FULFILLED,
                    reservation.getQuantity().negate(),
                    orderNumber,
                    reservation.getReservationKey(),
                    "Customer collected the order.",
                    actor
            );
        }

        log.info("Order inventory fulfilled: orderNumber={}, reservationCount={}",
                orderNumber, reservations.size());
    }

    /* Used by unpaid cancellation and automatic no-show finalization. */
    @Transactional
    public void cancelOrderInventory(
            String orderNumber,
            String reason,
            String actor
    ) {
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
                    actor
            );
        }

        log.info("Order inventory cancelled: orderNumber={}, reservationCount={}, actor={}",
                orderNumber, reservations.size(), actor);
    }

    private List<InventoryReservation> lockOrderReservations(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number is required.");
        }
        return reservationRepository.findByOrderNumberForUpdate(orderNumber.trim());
    }

    private Map<AllocationKey, InventoryDailyAllocation> lockAllocations(
            List<InventoryReservation> reservations
    ) {
        SortedSet<AllocationKey> keys = new TreeSet<>();
        for (InventoryReservation reservation : reservations) {
            keys.add(keyFor(reservation));
        }

        Map<AllocationKey, InventoryDailyAllocation> result = new HashMap<>();
        for (AllocationKey key : keys) {
            InventoryDailyAllocation allocation = allocationRepository
                    .findForUpdate(key.branchProductId(), key.serviceDate())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "ALLOCATION_NOT_FOUND",
                            "Inventory allocation no longer exists for this order."
                    ));
            result.put(key, allocation);
        }
        return result;
    }

    private InventoryDailyAllocation allocationFor(
            Map<AllocationKey, InventoryDailyAllocation> allocations,
            InventoryReservation reservation
    ) {
        InventoryDailyAllocation allocation = allocations.get(keyFor(reservation));
        if (allocation == null) {
            throw new InventoryNotFoundException(
                    "ALLOCATION_NOT_FOUND",
                    "Inventory allocation no longer exists for this order."
            );
        }
        return allocation;
    }

    private AllocationKey keyFor(InventoryReservation reservation) {
        return new AllocationKey(
                reservation.getAllocation().getServiceDate(),
                reservation.getAllocation().getBranchProduct().getId()
        );
    }

    private void subtractHeld(InventoryDailyAllocation allocation, BigDecimal quantity) {
        BigDecimal next = allocation.getHeldQuantity().subtract(quantity);
        if (next.signum() < 0) throw counterMismatch();
        allocation.setHeldQuantity(next);
    }

    private void subtractCommitted(InventoryDailyAllocation allocation, BigDecimal quantity) {
        BigDecimal next = allocation.getCommittedQuantity().subtract(quantity);
        if (next.signum() < 0) throw counterMismatch();
        allocation.setCommittedQuantity(next);
    }

    private InventoryConflictException counterMismatch() {
        return new InventoryConflictException(
                "INVENTORY_COUNTER_MISMATCH",
                "Inventory counters are inconsistent. Manual review is required."
        );
    }

    private InventoryConflictException invalidTransition(
            InventoryReservation reservation,
            String target
    ) {
        return new InventoryConflictException(
                "INVALID_INVENTORY_TRANSITION",
                "Inventory reservation " + reservation.getReservationKey()
                        + " cannot be " + target + " from " + reservation.getStatus() + "."
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "Order inventory cancelled.";
        return reason.trim().length() <= 300
                ? reason.trim()
                : reason.trim().substring(0, 300);
    }

    private record AllocationKey(
            java.time.LocalDate serviceDate,
            Long branchProductId
    ) implements Comparable<AllocationKey> {
        @Override
        public int compareTo(AllocationKey other) {
            int date = serviceDate.compareTo(other.serviceDate);
            return date != 0 ? date : branchProductId.compareTo(other.branchProductId);
        }
    }
}
