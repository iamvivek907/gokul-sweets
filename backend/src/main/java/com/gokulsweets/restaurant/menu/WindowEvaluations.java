package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/** Memoizes pure time-window calculations for one slot evaluation, never stock or product state. */
final class WindowEvaluations {
    private final ZonedDateTime at;
    private final Map<ServiceWindow, Evaluation> values = new HashMap<>();

    /** Creates an evaluation scope for one pickup timestamp; null means manual checks only. */
    WindowEvaluations(ZonedDateTime at) {
        this.at = at;
    }

    /** Immutable time-only result; product availability and dependencies remain separate. */
    record Evaluation(boolean active, Instant nextChange) {}

    /**
     * Calculates each distinct combination of hours and weekdays once within this scope.
     *
     * @param window immutable service hours and weekday mask
     * @return whether the window is open and its next genuine boundary
     */
    Evaluation get(ServiceWindow window) {
        long started = MethodTiming.start(WindowEvaluations.class, "get(ServiceWindow)");
        try {
            return values.computeIfAbsent(
                    window,
                    value ->
                            new Evaluation(
                                    at == null || value.contains(at),
                                    at == null ? null : value.nextChange(at)));
        } finally {
            MethodTiming.finish(started, WindowEvaluations.class, "get(ServiceWindow)");
        }
    }
}
