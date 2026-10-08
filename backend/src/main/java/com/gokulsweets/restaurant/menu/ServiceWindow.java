package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.time.*;

/** Start-inclusive/end-exclusive. Overnight windows belong to their opening weekday. */
public record ServiceWindow(LocalTime startsAt, LocalTime endsAt, int weekdays) {

    /**
     * Containses the operation.
     *
     * @param now the now
     * @return the contains result
     */
    public boolean contains(ZonedDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ServiceWindow.class, "contains(ZonedDateTime)");
        try {
            if (startsAt == null)
                return (weekdays & (1 << (now.getDayOfWeek().getValue() - 1))) != 0;
            var time = now.toLocalTime();
            boolean overnight = endsAt.isBefore(startsAt);
            var day =
                    overnight && time.isBefore(endsAt)
                            ? now.minusDays(1).getDayOfWeek()
                            : now.getDayOfWeek();
            boolean selected = (weekdays & (1 << (day.getValue() - 1))) != 0;
            return selected
                    && (overnight
                            ? !time.isBefore(startsAt) || time.isBefore(endsAt)
                            : !time.isBefore(startsAt) && time.isBefore(endsAt));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ServiceWindow.class, "contains(ZonedDateTime)");
        }
    }

    /**
     * Nexts change.
     *
     * @param now the now
     * @return the next change result
     */
    public Instant nextChange(ZonedDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ServiceWindow.class, "nextChange(ZonedDateTime)");
        try {
            Instant next = null;
            for (int i = -1; i <= 8; i++) {
                var date = now.toLocalDate().plusDays(i);
                if ((weekdays & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) continue;
                var start =
                        date.atTime(startsAt == null ? LocalTime.MIDNIGHT : startsAt)
                                .atZone(now.getZone())
                                .toInstant();
                var end =
                        (startsAt == null || endsAt.isBefore(startsAt) ? date.plusDays(1) : date)
                                .atTime(endsAt == null ? LocalTime.MIDNIGHT : endsAt)
                                .atZone(now.getZone())
                                .toInstant();
                for (var boundary : new Instant[] {start, end}) {
                    var atBoundary = boundary.atZone(now.getZone());
                    if (boundary.isAfter(now.toInstant())
                            && (next == null || boundary.isBefore(next))
                            && contains(atBoundary.minusNanos(1)) != contains(atBoundary))
                        next = boundary;
                }
            }
            return next;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ServiceWindow.class, "nextChange(ZonedDateTime)");
        }
    }
}
