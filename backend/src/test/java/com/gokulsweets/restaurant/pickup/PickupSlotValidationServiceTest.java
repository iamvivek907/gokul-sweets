package com.gokulsweets.restaurant.pickup;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PickupSlotValidationServiceTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Test
    void rejectsPastDateSlot() {
        PickupSlotValidationService service = serviceAt("2026-09-23T10:00:00Z");
        PickupSlot slot = slot(LocalDate.of(2026, 9, 22), LocalTime.of(18, 0), LocalTime.of(18, 30));

        assertThatThrownBy(() -> service.validateNotPassed(slot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already passed");
    }

    @Test
    void rejectsElapsedSameDaySlotByEndTime() {
        PickupSlotValidationService service = serviceAt("2026-09-23T14:35:00+05:30");
        PickupSlot slot = slot(LocalDate.of(2026, 9, 23), LocalTime.of(14, 0), LocalTime.of(14, 30));

        assertThatThrownBy(() -> service.validateNotPassed(slot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already passed");
    }

    @Test
    void acceptsFutureSlot() {
        PickupSlotValidationService service = serviceAt("2026-09-23T14:00:00+05:30");
        PickupSlot slot = slot(LocalDate.of(2026, 9, 24), LocalTime.of(10, 0), LocalTime.of(10, 30));

        assertThatCode(() -> service.validateNotPassed(slot))
                .doesNotThrowAnyException();
    }

    private PickupSlotValidationService serviceAt(String timestamp) {
        Clock clock = Clock.fixed(
                toInstant(timestamp),
                INDIA
        );
        return new PickupSlotValidationService(clock);
    }

    private Instant toInstant(String timestamp) {
        if (timestamp.endsWith("Z")) {
            return Instant.parse(timestamp);
        }
        return OffsetDateTime.parse(timestamp).toInstant();
    }

    private PickupSlot slot(LocalDate date, LocalTime start, LocalTime end) {
        PickupSlot slot = new PickupSlot();
        slot.setId(100L);
        slot.setSlotDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        return slot;
    }
}
