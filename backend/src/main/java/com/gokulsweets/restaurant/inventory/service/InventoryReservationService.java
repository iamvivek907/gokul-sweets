package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.model.CreateInventoryHoldCommand;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationService {

    private final InventoryProperties properties;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryAvailabilityService availabilityService;
    private final InventoryLedgerService ledgerService;
    private final InventoryQuantityService quantityService;
    private final Clock inventoryClock;

    @Transactional
    public InventoryReservation createHold(
            CreateInventoryHoldCommand command
    ) {
        validateCommand(command);

        InventoryReservation existing = reservationRepository
                .findByReservationKey(command.reservationKey())
                .orElse(null);

        if (existing != null) {
            validateIdempotentRetry(existing, command);
            return existing;
        }

        InventoryDailyAllocation allocation = allocationRepository
                .findForUpdate(
                        command.branchProductId(),
                        command.serviceDate()
                )
                .orElseThrow(() -> new InventoryNotFoundException(
                        "ALLOCATION_NOT_FOUND",
                        "No approved inventory exists for this product and pickup date."
                ));

        /*
         * Re-check after locking the allocation row. Two requests
         * carrying the same key may both pass the first read before
         * either transaction commits. The allocation lock serializes
         * them and this second lookup preserves idempotency.
         */
        existing = reservationRepository
                .findByReservationKey(command.reservationKey())
                .orElse(null);

        if (existing != null) {
            validateIdempotentRetry(existing, command);
            return existing;
        }

        BranchInventoryPolicy policy = policyRepository
                .findByBranchProductId(command.branchProductId())
                .orElseThrow(() -> new InventoryNotFoundException(
                        "INVENTORY_POLICY_NOT_FOUND",
                        "Inventory policy is not configured for this product."
                ));

        BigDecimal requestedQuantity =
                quantityService.normalizePositive(
                        command.quantity(),
                        policy.getInventoryUnit(),
                        "Requested quantity"
                );

        InventoryAvailability availability =
                availabilityService.calculate(allocation, policy);

        if (
                !availability.orderable()
                        || availability.availableQuantity().compareTo(
                        requestedQuantity
                ) < 0
        ) {
            throw new InventoryConflictException(
                    "INSUFFICIENT_INVENTORY",
                    availability.unavailableReason() == null
                            ? "The requested quantity is no longer available."
                            : availability.unavailableReason()
            );
        }

        allocation.setHeldQuantity(
                allocation.getHeldQuantity().add(requestedQuantity)
        );

        InventoryReservation reservation = new InventoryReservation();
        reservation.setAllocation(allocation);
        reservation.setReservationKey(command.reservationKey());
        reservation.setOrderNumber(command.orderNumber());
        reservation.setQuantity(requestedQuantity);
        reservation.setStatus(InventoryReservationStatus.TEMPORARY_HOLD);
        reservation.setExpiresAt(
                LocalDateTime.now(inventoryClock)
                        .plusMinutes(properties.getTemporaryHoldMinutes())
        );

        InventoryReservation saved = reservationRepository.save(reservation);

        ledgerService.record(
                allocation,
                saved,
                InventoryTransactionType.TEMPORARY_HOLD,
                requestedQuantity.negate(),
                command.orderNumber(),
                command.reservationKey(),
                "Temporary checkout inventory hold created.",
                null
        );

        log.info(
                "Inventory hold created: reservationKey={}, branchProductId={}, serviceDate={}, quantity={}, expiresAt={}",
                mask(command.reservationKey()),
                command.branchProductId(),
                command.serviceDate(),
                requestedQuantity,
                saved.getExpiresAt()
        );

        return saved;
    }

    @Transactional
    public void confirmHold(
            String reservationKey
    ) {
        InventoryReservation reservation = getForUpdate(reservationKey);

        if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED) {
            return;
        }

        if (reservation.getStatus() != InventoryReservationStatus.TEMPORARY_HOLD) {
            throw invalidTransition(reservation, "confirmed");
        }

        if (isExpired(reservation)) {
            expireHold(reservation);
            throw new InventoryConflictException(
                    "INVENTORY_HOLD_EXPIRED",
                    "The inventory reservation expired. Please review availability again."
            );
        }

        InventoryDailyAllocation allocation = lockAllocation(reservation);
        allocation.setHeldQuantity(
                allocation.getHeldQuantity().subtract(reservation.getQuantity())
        );
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
                BigDecimal.ZERO.subtract(reservation.getQuantity()),
                reservation.getOrderNumber(),
                reservation.getReservationKey(),
                "Temporary hold converted to a confirmed commitment.",
                null
        );
    }

    @Transactional
    public void releaseHold(
            String reservationKey,
            String reason
    ) {
        InventoryReservation reservation = getForUpdate(reservationKey);

        if (
                reservation.getStatus() == InventoryReservationStatus.RELEASED
                        || reservation.getStatus() == InventoryReservationStatus.EXPIRED
        ) {
            return;
        }

        if (reservation.getStatus() != InventoryReservationStatus.TEMPORARY_HOLD) {
            throw invalidTransition(reservation, "released");
        }

        InventoryDailyAllocation allocation = lockAllocation(reservation);
        allocation.setHeldQuantity(
                allocation.getHeldQuantity().subtract(reservation.getQuantity())
        );

        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservation.setReleasedAt(LocalDateTime.now(inventoryClock));
        reservation.setReleaseReason(normalizeReason(reason));

        ledgerService.record(
                allocation,
                reservation,
                InventoryTransactionType.HOLD_RELEASED,
                reservation.getQuantity(),
                reservation.getOrderNumber(),
                reservation.getReservationKey(),
                reservation.getReleaseReason(),
                null
        );
    }

    @Transactional
    public boolean expireHoldIfDue(
            String reservationKey
    ) {
        InventoryReservation reservation = getForUpdate(reservationKey);

        if (
                reservation.getStatus()
                        != InventoryReservationStatus.TEMPORARY_HOLD
        ) {
            return false;
        }

        if (!isExpired(reservation)) {
            return false;
        }

        expireHold(reservation);

        log.info(
                "Inventory hold expired: reservationKey={}, branchProductId={}, quantity={}",
                mask(reservationKey),
                reservation.getAllocation().getBranchProduct().getId(),
                reservation.getQuantity()
        );

        return true;
    }

    private void expireHold(
            InventoryReservation reservation
    ) {
        InventoryDailyAllocation allocation = lockAllocation(reservation);
        allocation.setHeldQuantity(
                allocation.getHeldQuantity().subtract(reservation.getQuantity())
        );
        reservation.setStatus(InventoryReservationStatus.EXPIRED);
        reservation.setReleasedAt(LocalDateTime.now(inventoryClock));
        reservation.setReleaseReason("Temporary inventory hold expired.");

        ledgerService.record(
                allocation,
                reservation,
                InventoryTransactionType.HOLD_RELEASED,
                reservation.getQuantity(),
                reservation.getOrderNumber(),
                reservation.getReservationKey(),
                reservation.getReleaseReason(),
                null
        );
    }

    private InventoryReservation getForUpdate(
            String reservationKey
    ) {
        return reservationRepository
                .findByReservationKeyForUpdate(reservationKey)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "INVENTORY_RESERVATION_NOT_FOUND",
                        "Inventory reservation does not exist."
                ));
    }

    private InventoryDailyAllocation lockAllocation(
            InventoryReservation reservation
    ) {
        InventoryDailyAllocation current = reservation.getAllocation();

        return allocationRepository
                .findForUpdate(
                        current.getBranchProduct().getId(),
                        current.getServiceDate()
                )
                .orElseThrow(() -> new InventoryNotFoundException(
                        "ALLOCATION_NOT_FOUND",
                        "Inventory allocation no longer exists."
                ));
    }

    private boolean isExpired(
            InventoryReservation reservation
    ) {
        return reservation.getExpiresAt() != null
                && !reservation.getExpiresAt().isAfter(
                LocalDateTime.now(inventoryClock)
        );
    }

    private void validateCommand(
            CreateInventoryHoldCommand command
    ) {
        if (
                command == null
                        || command.reservationKey() == null
                        || command.reservationKey().isBlank()
                        || command.branchProductId() == null
                        || command.serviceDate() == null
                        || command.quantity() == null
                        || command.quantity().compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new IllegalArgumentException(
                    "A valid inventory hold request is required."
            );
        }
    }

    private void validateIdempotentRetry(
            InventoryReservation existing,
            CreateInventoryHoldCommand command
    ) {
        boolean sameRequest =
                existing.getAllocation().getBranchProduct().getId()
                        .equals(command.branchProductId())
                        && existing.getAllocation().getServiceDate()
                        .equals(command.serviceDate())
                        && existing.getQuantity().compareTo(command.quantity()) == 0;

        if (!sameRequest) {
            throw new InventoryConflictException(
                    "RESERVATION_KEY_REUSED",
                    "The inventory reservation key was already used for a different request."
            );
        }
    }

    private InventoryConflictException invalidTransition(
            InventoryReservation reservation,
            String action
    ) {
        return new InventoryConflictException(
                "INVALID_RESERVATION_TRANSITION",
                "Inventory reservation in status "
                        + reservation.getStatus()
                        + " cannot be "
                        + action
                        + "."
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Inventory hold released.";
        }
        return reason.trim();
    }

    private String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 8) + "...";
    }
}
