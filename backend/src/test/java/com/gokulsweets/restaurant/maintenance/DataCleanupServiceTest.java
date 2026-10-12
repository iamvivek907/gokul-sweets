package com.gokulsweets.restaurant.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

class DataCleanupServiceTest {
    private final DataCleanupService.Config config =
            new DataCleanupService.Config(true, "03:30", 90, 0);
    private final Instant before = Instant.parse("2026-10-08T21:59:00Z"); // 03:29 IST on October 9
    private final Instant after = Instant.parse("2026-10-08T22:00:00Z");

    @Test
    void followsIstDateAndCatchesUpOnlyOnceAfterConfiguredTime() {
        assertThat(DataCleanupService.due(config, null, "NEVER", null, before)).isFalse();
        assertThat(
                        DataCleanupService.due(
                                config, LocalDate.of(2026, 10, 8), "SUCCEEDED", null, after))
                .isTrue();
        assertThat(
                        DataCleanupService.due(
                                config, LocalDate.of(2026, 10, 9), "SUCCEEDED", null, after))
                .isFalse();
        assertThat(DataCleanupService.due(config, LocalDate.of(2026, 10, 9), "FAILED", null, after))
                .isFalse();
    }

    @Test
    void disabledScheduleStaysOffAndOnlyExpiredCrashedRunsRecoverThatDay() {
        assertThat(
                        DataCleanupService.due(
                                new DataCleanupService.Config(false, "03:30", 90, 0),
                                null,
                                "NEVER",
                                null,
                                after))
                .isFalse();
        assertThat(
                        DataCleanupService.due(
                                config,
                                LocalDate.of(2026, 10, 9),
                                "RUNNING",
                                after.plusSeconds(30),
                                after))
                .isFalse();
        assertThat(
                        DataCleanupService.due(
                                config,
                                LocalDate.of(2026, 10, 9),
                                "RUNNING",
                                after.minusSeconds(1),
                                after))
                .isTrue();
    }
}
