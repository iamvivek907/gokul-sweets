package com.gokulsweets.restaurant.pickup.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates pickup slot reservation operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickupSlotReservationService {

    private final PickupSlotRepository pickupSlotRepository;

    /**
     * Reserves normal capacity.
     *
     * @param slotId the slot id
     */
    @Transactional
    public void reserveNormalCapacity(Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotReservationService.class, "reserveNormalCapacity(Long)");
        try {
            log.debug("Attempting normal pickup capacity reservation: slotId={}", slotId);
            int updatedRows = pickupSlotRepository.reserveNormalCapacity(slotId);
            if (updatedRows == 0) {
                log.warn("Normal pickup capacity reservation failed: slotId={}", slotId);
                throw new IllegalStateException("Pickup slot is no longer available.");
            }
            log.info("Normal pickup capacity reserved: slotId={}", slotId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotReservationService.class,
                    "reserveNormalCapacity(Long)");
        }
    }

    /**
     * Reserves priority capacity.
     *
     * @param slotId the slot id
     */
    @Transactional
    public void reservePriorityCapacity(Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotReservationService.class, "reservePriorityCapacity(Long)");
        try {
            log.debug("Attempting priority pickup capacity reservation: slotId={}", slotId);
            int updatedRows = pickupSlotRepository.reservePriorityCapacity(slotId);
            if (updatedRows == 0) {
                log.warn("Priority pickup capacity reservation failed: slotId={}", slotId);
                throw new IllegalStateException("Priority pickup capacity is no longer available.");
            }
            log.info("Priority pickup capacity reserved: slotId={}", slotId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotReservationService.class,
                    "reservePriorityCapacity(Long)");
        }
    }

    /**
     * Releases normal capacity.
     *
     * @param slotId the slot id
     */
    @Transactional
    public void releaseNormalCapacity(Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotReservationService.class, "releaseNormalCapacity(Long)");
        try {
            log.debug("Releasing normal pickup capacity: slotId={}", slotId);
            int updatedRows = pickupSlotRepository.releaseNormalCapacity(slotId);
            if (updatedRows == 0) {
                log.warn("Normal pickup capacity release failed: slotId={}", slotId);
                throw new IllegalStateException("Unable to release pickup capacity.");
            }
            log.info("Normal pickup capacity released: slotId={}", slotId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotReservationService.class,
                    "releaseNormalCapacity(Long)");
        }
    }

    /**
     * Releases priority capacity.
     *
     * @param slotId the slot id
     */
    @Transactional
    public void releasePriorityCapacity(Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotReservationService.class, "releasePriorityCapacity(Long)");
        try {
            log.debug("Releasing priority pickup capacity: slotId={}", slotId);
            int updatedRows = pickupSlotRepository.releasePriorityCapacity(slotId);
            if (updatedRows == 0) {
                log.warn("Priority pickup capacity release failed: slotId={}", slotId);
                throw new IllegalStateException("Unable to release priority pickup capacity.");
            }
            log.info("Priority pickup capacity released: slotId={}", slotId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotReservationService.class,
                    "releasePriorityCapacity(Long)");
        }
    }
}
