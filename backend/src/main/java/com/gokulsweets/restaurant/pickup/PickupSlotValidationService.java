package com.gokulsweets.restaurant.pickup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
@Slf4j
public class PickupSlotValidationService {

    private final Clock clock;

    public PickupSlotValidationService() {
        this(Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    public PickupSlotValidationService(Clock clock) {
        this.clock = clock;
    }

    public void validateNotPassed(PickupSlot slot) {

        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        if (slot.getSlotDate().isBefore(today)) {

            log.warn(
                    "Pickup slot has passed: slotId={}, slotDate={}",
                    slot.getId(),
                    slot.getSlotDate()
            );

            throw new IllegalArgumentException(
                    "This pickup slot has already passed."
            );
        }

        if (slot.getSlotDate().isEqual(today)
                && !slot.getEndTime().isAfter(now)) {

            log.warn(
                    "Pickup slot has passed: slotId={}, date={}, endTime={}, currentTime={}",
                    slot.getId(),
                    slot.getSlotDate(),
                    slot.getEndTime(),
                    now
            );

            throw new IllegalArgumentException(
                    "This pickup slot has already passed."
            );
        }
    }
}