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
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/** Coordinates inventory reservation operations. */
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

    /**
     * Creates hold.
     *
     * @param command the command
     * @return the create hold result
     */
    @Transactional
    public InventoryReservation createHold(CreateInventoryHoldCommand command) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class,
                        "createHold(CreateInventoryHoldCommand)");
        try {
            validateCommand(command);
            InventoryReservation existing =
                    reservationRepository
                            .findByReservationKey(command.reservationKey())
                            .orElse(null);
            if (existing != null) {
                validateIdempotentRetry(existing, command);
                return existing;
            }
            InventoryDailyAllocation allocation =
                    allocationRepository
                            .findForUpdate(command.branchProductId(), command.serviceDate())
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundException(
                                                    "ALLOCATION_NOT_FOUND",
                                                    "No approved inventory exists for this product"
                                                            + " and pickup date."));
            /*
             * Re-check after locking the allocation row. Two requests
             * carrying the same key may both pass the first read before
             * either transaction commits. The allocation lock serializes
             * them and this second lookup preserves idempotency.
             */
            existing =
                    reservationRepository
                            .findByReservationKey(command.reservationKey())
                            .orElse(null);
            if (existing != null) {
                validateIdempotentRetry(existing, command);
                return existing;
            }
            BranchInventoryPolicy policy =
                    policyRepository
                            .findByBranchProductId(command.branchProductId())
                            .orElseThrow(
                                    () ->
                                            new InventoryNotFoundException(
                                                    "INVENTORY_POLICY_NOT_FOUND",
                                                    "Inventory policy is not configured for this"
                                                            + " product."));
            BigDecimal requestedQuantity =
                    quantityService.normalizePositive(
                            command.quantity(), policy.getInventoryUnit(), "Requested quantity");
            InventoryAvailability availability = availabilityService.calculate(allocation, policy);
            if (!availability.orderable()
                    || availability.availableQuantity().compareTo(requestedQuantity) < 0) {
                throw new InventoryConflictException(
                        "INSUFFICIENT_INVENTORY",
                        availability.unavailableReason() == null
                                ? "The requested quantity is no longer available."
                                : availability.unavailableReason());
            }
            allocation.setHeldQuantity(allocation.getHeldQuantity().add(requestedQuantity));
            InventoryReservation reservation = new InventoryReservation();
            reservation.setAllocation(allocation);
            reservation.setReservationKey(command.reservationKey());
            reservation.setOrderNumber(command.orderNumber());
            reservation.setQuantity(requestedQuantity);
            reservation.setStatus(InventoryReservationStatus.TEMPORARY_HOLD);
            reservation.setExpiresAt(
                    LocalDateTime.now(inventoryClock)
                            .plusMinutes(properties.getTemporaryHoldMinutes()));
            InventoryReservation saved = reservationRepository.save(reservation);
            ledgerService.record(
                    allocation,
                    saved,
                    InventoryTransactionType.TEMPORARY_HOLD,
                    requestedQuantity.negate(),
                    command.orderNumber(),
                    command.reservationKey(),
                    "Temporary checkout inventory hold created.",
                    null);
            log.info(
                    "Inventory hold created: reservationKey={}, branchProductId={}, serviceDate={},"
                            + " quantity={}, expiresAt={}",
                    mask(command.reservationKey()),
                    command.branchProductId(),
                    command.serviceDate(),
                    requestedQuantity,
                    saved.getExpiresAt());
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "createHold(CreateInventoryHoldCommand)");
        }
    }

    /**
     * Confirms hold.
     *
     * @param reservationKey the reservation key
     */
    @Transactional
    public void confirmHold(String reservationKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryReservationService.class, "confirmHold(String)");
        try {
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
                        "The inventory reservation expired. Please review availability again.");
            }
            InventoryDailyAllocation allocation = lockAllocation(reservation);
            allocation.setHeldQuantity(
                    allocation.getHeldQuantity().subtract(reservation.getQuantity()));
            allocation.setCommittedQuantity(
                    allocation.getCommittedQuantity().add(reservation.getQuantity()));
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
                    null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "confirmHold(String)");
        }
    }

    /**
     * Releases hold.
     *
     * @param reservationKey the reservation key
     * @param reason the reason
     */
    @Transactional
    public void releaseHold(String reservationKey, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryReservationService.class, "releaseHold(String,String)");
        try {
            InventoryReservation reservation = getForUpdate(reservationKey);
            if (reservation.getStatus() == InventoryReservationStatus.RELEASED
                    || reservation.getStatus() == InventoryReservationStatus.EXPIRED) {
                return;
            }
            if (reservation.getStatus() != InventoryReservationStatus.TEMPORARY_HOLD) {
                throw invalidTransition(reservation, "released");
            }
            InventoryDailyAllocation allocation = lockAllocation(reservation);
            allocation.setHeldQuantity(
                    allocation.getHeldQuantity().subtract(reservation.getQuantity()));
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
                    null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "releaseHold(String,String)");
        }
    }

    /**
     * Expires hold if due.
     *
     * @param reservationKey the reservation key
     * @return the expire hold if due result
     */
    @Transactional
    public boolean expireHoldIfDue(String reservationKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryReservationService.class, "expireHoldIfDue(String)");
        try {
            InventoryReservation reservation = getForUpdate(reservationKey);
            if (reservation.getStatus() != InventoryReservationStatus.TEMPORARY_HOLD) {
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
                    reservation.getQuantity());
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "expireHoldIfDue(String)");
        }
    }

    /**
     * Expires hold.
     *
     * @param reservation the reservation
     */
    private void expireHold(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class, "expireHold(InventoryReservation)");
        try {
            InventoryDailyAllocation allocation = lockAllocation(reservation);
            allocation.setHeldQuantity(
                    allocation.getHeldQuantity().subtract(reservation.getQuantity()));
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
                    null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "expireHold(InventoryReservation)");
        }
    }

    /**
     * Returns for update.
     *
     * @param reservationKey the reservation key
     * @return the get for update result
     */
    private InventoryReservation getForUpdate(String reservationKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryReservationService.class, "getForUpdate(String)");
        try {
            return reservationRepository
                    .findByReservationKeyForUpdate(reservationKey)
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "INVENTORY_RESERVATION_NOT_FOUND",
                                            "Inventory reservation does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "getForUpdate(String)");
        }
    }

    /**
     * Locks allocation.
     *
     * @param reservation the reservation
     * @return the lock allocation result
     */
    private InventoryDailyAllocation lockAllocation(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class, "lockAllocation(InventoryReservation)");
        try {
            InventoryDailyAllocation current = reservation.getAllocation();
            return allocationRepository
                    .findForUpdate(current.getBranchProduct().getId(), current.getServiceDate())
                    .orElseThrow(
                            () ->
                                    new InventoryNotFoundException(
                                            "ALLOCATION_NOT_FOUND",
                                            "Inventory allocation no longer exists."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "lockAllocation(InventoryReservation)");
        }
    }

    /**
     * Reports whether expired.
     *
     * @param reservation the reservation
     * @return the is expired result
     */
    private boolean isExpired(InventoryReservation reservation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class, "isExpired(InventoryReservation)");
        try {
            return reservation.getExpiresAt() != null
                    && !reservation.getExpiresAt().isAfter(LocalDateTime.now(inventoryClock));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "isExpired(InventoryReservation)");
        }
    }

    /**
     * Validates command.
     *
     * @param command the command
     */
    private void validateCommand(CreateInventoryHoldCommand command) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class,
                        "validateCommand(CreateInventoryHoldCommand)");
        try {
            if (command == null
                    || command.reservationKey() == null
                    || command.reservationKey().isBlank()
                    || command.branchProductId() == null
                    || command.serviceDate() == null
                    || command.quantity() == null
                    || command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("A valid inventory hold request is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "validateCommand(CreateInventoryHoldCommand)");
        }
    }

    /**
     * Validates idempotent retry.
     *
     * @param existing the existing
     * @param command the command
     */
    private void validateIdempotentRetry(
            InventoryReservation existing, CreateInventoryHoldCommand command) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class,
                        "validateIdempotentRetry(InventoryReservation,CreateInventoryHoldCommand)");
        try {
            boolean sameRequest =
                    existing.getAllocation()
                                    .getBranchProduct()
                                    .getId()
                                    .equals(command.branchProductId())
                            && existing.getAllocation()
                                    .getServiceDate()
                                    .equals(command.serviceDate())
                            && existing.getQuantity().compareTo(command.quantity()) == 0;
            if (!sameRequest) {
                throw new InventoryConflictException(
                        "RESERVATION_KEY_REUSED",
                        "The inventory reservation key was already used for a different request.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "validateIdempotentRetry(InventoryReservation,CreateInventoryHoldCommand)");
        }
    }

    /**
     * Invalids transition.
     *
     * @param reservation the reservation
     * @param action the action
     * @return the invalid transition result
     */
    private InventoryConflictException invalidTransition(
            InventoryReservation reservation, String action) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryReservationService.class,
                        "invalidTransition(InventoryReservation,String)");
        try {
            return new InventoryConflictException(
                    "INVALID_RESERVATION_TRANSITION",
                    "Inventory reservation in status "
                            + reservation.getStatus()
                            + " cannot be "
                            + action
                            + ".");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
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
                MethodTiming.start(InventoryReservationService.class, "normalizeReason(String)");
        try {
            if (reason == null || reason.isBlank()) {
                return "Inventory hold released.";
            }
            return reason.trim();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryReservationService.class,
                    "normalizeReason(String)");
        }
    }

    /**
     * Masks the operation.
     *
     * @param value the value
     * @return the mask result
     */
    private String mask(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryReservationService.class, "mask(String)");
        try {
            if (value == null || value.length() <= 8) {
                return "***";
            }
            return value.substring(0, 8) + "...";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryReservationService.class, "mask(String)");
        }
    }
}
