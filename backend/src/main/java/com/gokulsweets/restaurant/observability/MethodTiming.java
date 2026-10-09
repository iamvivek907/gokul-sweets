package com.gokulsweets.restaurant.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Fail-open, argument-free timing for explicitly declared backend methods.
 *
 * <p>Durations are inclusive: a caller includes time spent in nested calls, database operations,
 * locks and external I/O. This measures elapsed time, not CPU usage. The generated {@code finally}
 * blocks preserve returns, exceptions and existing transaction boundaries. No argument, result,
 * exception message, customer identifier or credential is recorded.
 */
public final class MethodTiming {

    private static final Logger LOG = LoggerFactory.getLogger(MethodTiming.class);

    private static volatile boolean enabled;

    private static volatile long slowThresholdNanos = TimeUnit.SECONDS.toNanos(1);

    /** Creates a method timing instance. */
    private MethodTiming() {}

    /**
     * Starts an elapsed-time measurement when diagnostics are enabled.
     *
     * @param owner declaring class, used only as a diagnostic label
     * @param signature method name and parameter types, never parameter values
     * @return a monotonic start marker, or zero when timing is disabled or logging fails
     */
    public static long start(Class<?> owner, String signature) {
        if (!enabled) {
            return 0;
        }
        try {
            if (!LOG.isWarnEnabled() && !LOG.isDebugEnabled() && !LOG.isTraceEnabled()) {
                return 0;
            }
            long started = System.nanoTime();
            LOG.trace("method_enter class={} method={}", owner.getName(), signature);
            return started;
        } catch (RuntimeException loggingFailure) {
            return 0;
        }
    }

    /**
     * Records elapsed duration on normal or exceptional exit without changing the method outcome.
     *
     * @param started marker returned by {@link #start(Class, String)}
     * @param owner declaring class, used only as a diagnostic label
     * @param signature method name and parameter types, never parameter values
     */
    public static void finish(long started, Class<?> owner, String signature) {
        if (started == 0) {
            return;
        }
        try {
            long elapsed = Math.max(0, System.nanoTime() - started);
            if (elapsed >= slowThresholdNanos && LOG.isWarnEnabled()) {
                LOG.warn(
                        "method_slow class={} method={} durationMs={}",
                        owner.getName(),
                        signature,
                        elapsed / 1_000_000.0);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "method_exit class={} method={} durationMs={}",
                        owner.getName(),
                        signature,
                        elapsed / 1_000_000.0);
            }
        } catch (RuntimeException loggingFailure) {
            // Diagnostics must not replace a business return value or an existing exception.
        }
    }

    /**
     * Applies application-level diagnostic configuration; changing it does not mutate business
     * data.
     *
     * @param active whether new method measurements should start
     * @param slowThresholdMillis elapsed duration at which WARN is emitted; negatives become zero
     */
    static void configure(boolean active, long slowThresholdMillis) {
        slowThresholdNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, slowThresholdMillis));
        enabled = active;
    }
}
