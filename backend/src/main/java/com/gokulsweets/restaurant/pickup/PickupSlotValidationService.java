package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Coordinates pickup slot validation operations. */
@Service
@Slf4j
public class PickupSlotValidationService {

    private final Clock clock;

    /** Creates a pickup slot validation service instance. */
    public PickupSlotValidationService() {
        this(Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /**
     * Creates a pickup slot validation service instance.
     *
     * @param clock the clock
     */
    public PickupSlotValidationService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Validates not passed.
     *
     * @param slot the slot
     */
    public void validateNotPassed(PickupSlot slot) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupSlotValidationService.class, "validateNotPassed(PickupSlot)");
        try {
            LocalDate today = LocalDate.now(clock);
            LocalTime now = LocalTime.now(clock);
            if (slot.getSlotDate().isBefore(today)) {
                log.warn(
                        "Pickup slot has passed: slotId={}, slotDate={}",
                        slot.getId(),
                        slot.getSlotDate());
                throw new IllegalArgumentException("This pickup slot has already passed.");
            }
            if (slot.getSlotDate().isEqual(today) && !slot.getEndTime().isAfter(now)) {
                log.warn(
                        "Pickup slot has passed: slotId={}, date={}, endTime={}, currentTime={}",
                        slot.getId(),
                        slot.getSlotDate(),
                        slot.getEndTime(),
                        now);
                throw new IllegalArgumentException("This pickup slot has already passed.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupSlotValidationService.class,
                    "validateNotPassed(PickupSlot)");
        }
    }
}
