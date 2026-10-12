package com.gokulsweets.restaurant.menu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class ServiceWindowTest {
    private ZonedDateTime at(String date) {
        return LocalDateTime.parse(date).atZone(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void exactOpeningClosingAndIstBoundaries() {
        var breakfast = new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), 127);
        assertThat(breakfast.contains(at("2026-10-05T08:59:59"))).isFalse();
        assertThat(breakfast.contains(at("2026-10-05T09:00:00"))).isTrue();
        assertThat(breakfast.contains(at("2026-10-05T10:59:59"))).isTrue();
        assertThat(breakfast.contains(at("2026-10-05T11:00:00"))).isFalse();
        assertThat(breakfast.nextChange(at("2026-10-05T11:00:00")))
                .isEqualTo(Instant.parse("2026-10-06T03:30:00Z"));
    }

    @Test
    void overnightWindowUsesOpeningWeekday() {
        var monday = new ServiceWindow(LocalTime.of(22, 0), LocalTime.of(2, 0), 1);
        assertThat(monday.contains(at("2026-10-05T23:59:59"))).isTrue();
        assertThat(monday.contains(at("2026-10-06T01:59:59"))).isTrue();
        assertThat(monday.contains(at("2026-10-06T02:00:00"))).isFalse();
        assertThat(monday.contains(at("2026-10-06T23:00:00"))).isFalse();
    }

    @Test
    void allDaySelectedDaysHaveNextOpening() {
        var weekdays = new ServiceWindow(null, null, 31);
        assertThat(weekdays.contains(at("2026-10-10T10:00:00"))).isFalse();
        assertThat(weekdays.nextChange(at("2026-10-10T10:00:00")))
                .isEqualTo(at("2026-10-12T00:00:00").toInstant());
    }

    @Test
    void consecutiveAllDayWindowsDoNotExpireAtAnUnchangedMidnight() {
        assertThat(new ServiceWindow(null, null, 127).nextChange(at("2026-10-05T23:59:59")))
                .isNull();
        assertThat(new ServiceWindow(null, null, 3).nextChange(at("2026-10-05T23:59:59")))
                .isEqualTo(at("2026-10-07T00:00:00").toInstant());
    }
}
