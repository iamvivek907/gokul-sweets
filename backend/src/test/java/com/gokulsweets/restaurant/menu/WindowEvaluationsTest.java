package com.gokulsweets.restaurant.menu;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class WindowEvaluationsTest {
    private final ZoneId ist = ZoneId.of("Asia/Kolkata");

    @Test
    void identicalHoursAcrossProductsCalculateBoundariesOnlyOncePerSlot() {
        var at = LocalDateTime.of(2026, 10, 5, 10, 0).atZone(ist);
        var first = spy(new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), 127));
        var evaluations = new WindowEvaluations(at);
        var result = evaluations.get(first);
        for (int item = 0; item < 500; item++) {
            assertThat(evaluations.get(first)).isSameAs(result);
        }
        verify(first, times(1)).nextChange(at);
        assertThat(result.active()).isTrue();
        assertThat(new WindowEvaluations(at.plusHours(1)).get(first).active()).isFalse();
        var records = new WindowEvaluations(at);
        assertThat(records.get(new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), 127)))
                .isSameAs(
                        records.get(
                                new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), 127)));
    }

    @Test
    void weeklyMasksOvernightAllDayAndExactBoundariesMatchUnmemoizedResults() {
        for (int weekdays = 0; weekdays <= 127; weekdays++) {
            for (var window :
                    new ServiceWindow[] {
                        new ServiceWindow(null, null, weekdays),
                        new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), weekdays),
                        new ServiceWindow(LocalTime.of(22, 0), LocalTime.of(2, 0), weekdays)
                    }) {
                for (String timestamp :
                        new String[] {
                            "2026-10-05T08:59:59",
                            "2026-10-05T09:00:00",
                            "2026-10-05T11:00:00",
                            "2026-10-05T22:00:00",
                            "2026-10-06T01:59:59",
                            "2026-10-06T02:00:00",
                            "2026-10-10T23:59:59"
                        }) {
                    var at = LocalDateTime.parse(timestamp).atZone(ist);
                    var evaluations = new WindowEvaluations(at);
                    var actual = evaluations.get(window);
                    assertThat(actual.active()).isEqualTo(window.contains(at));
                    assertThat(actual.nextChange()).isEqualTo(window.nextChange(at));
                    assertThat(evaluations.get(window)).isSameAs(actual);
                }
            }
        }
    }

    @Test
    void manualChecksNeverEvaluateDatedBoundaries() {
        var window = spy(new ServiceWindow(LocalTime.of(9, 0), LocalTime.of(11, 0), 127));
        var actual = new WindowEvaluations(null).get(window);
        assertThat(actual.active()).isTrue();
        assertThat(actual.nextChange()).isNull();
        verifyNoInteractions(window);
    }
}
