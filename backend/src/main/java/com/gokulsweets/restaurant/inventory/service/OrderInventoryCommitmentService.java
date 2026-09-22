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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderInventoryCommitmentService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryLedgerService ledgerService;
    private final Clock inventoryClock;

    @Transactional
    public void confirmPendingOrderHolds(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number is required.");
        }

        List<InventoryReservation> reservations = reservationRepository
                .findByOrderNumberForUpdate(orderNumber);

        if (reservations.isEmpty()) {
            log.debug(
                    "No inventory reservations exist for paid order: orderNumber={}",
                    orderNumber
            );
            return;
        }

        Map<Long, InventoryDailyAllocation> locked = new TreeMap<>();
        reservations.stream()
                .filter(item -> item.getStatus()
                        == InventoryReservationStatus.TEMPORARY_HOLD)
                .sorted(Comparator.comparing(item ->
                        item.getAllocation().getBranchProduct().getId()))
                .forEach(item -> {
                    Long branchProductId = item.getAllocation()
                            .getBranchProduct().getId();
                    locked.computeIfAbsent(
                            branchProductId,
                            ignored -> allocationRepository.findForUpdate(
                                            branchProductId,
                                            item.getAllocation().getServiceDate()
                                    )
                                    .orElseThrow(() ->
                                            new InventoryNotFoundException(
                                                    "ALLOCATION_NOT_FOUND",
                                                    "Inventory allocation no longer exists."
                                            ))
                    );
                });

        int confirmed = 0;
        LocalDateTime now = LocalDateTime.now(inventoryClock);

        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED
                    || reservation.getStatus() == InventoryReservationStatus.FULFILLED) {
                continue;
            }
            if (reservation.getStatus()
                    != InventoryReservationStatus.TEMPORARY_HOLD) {
                throw new InventoryConflictException(
                        "INVENTORY_HOLD_NOT_ACTIVE",
                        "Paid order inventory is no longer reserved. Manual reconciliation is required."
                );
            }

            Long branchProductId = reservation.getAllocation()
                    .getBranchProduct().getId();
            InventoryDailyAllocation allocation = locked.get(branchProductId);
            BigDecimal quantity = reservation.getQuantity();
            BigDecimal remainingHeld = allocation.getHeldQuantity()
                    .subtract(quantity);

            if (remainingHeld.compareTo(BigDecimal.ZERO) < 0) {
                throw new InventoryConflictException(
                        "INVENTORY_COUNTER_MISMATCH",
                        "Inventory reservation counters are inconsistent. Manual review is required."
                );
            }

            allocation.setHeldQuantity(remainingHeld);
            allocation.setCommittedQuantity(
                    allocation.getCommittedQuantity().add(quantity)
            );
            reservation.setStatus(InventoryReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(now);
            reservation.setExpiresAt(null);
            reservation.setReleasedAt(null);
            reservation.setReleaseReason(null);

            ledgerService.record(
                    allocation,
                    reservation,
                    InventoryTransactionType.HOLD_RELEASED,
                    quantity,
                    orderNumber,
                    reservation.getReservationKey(),
                    "Temporary checkout hold converted to a confirmed commitment.",
                    null
            );
            ledgerService.record(
                    allocation,
                    reservation,
                    InventoryTransactionType.COMMITMENT_CONFIRMED,
                    quantity.negate(),
                    orderNumber,
                    reservation.getReservationKey(),
                    "Paid order inventory committed.",
                    null
            );
            confirmed++;
        }

        if (confirmed > 0) {
            log.info(
                    "Paid-order inventory committed: orderNumber={}, reservationCount={}",
                    orderNumber,
                    confirmed
            );
        }
    }
}
