package com.gokulsweets.restaurant.maintenance;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Backend data cleanup scheduler contract and implementation. */
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupScheduler.class);

    private final DataCleanupService cleanup;

    /**
     * Attempts scheduled cleanup and logs failures so a failed poll does not prevent later
     * scheduled attempts.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void poll() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DataCleanupScheduler.class, "poll()");
        try {
            try {
                cleanup.scheduledRun();
            } catch (RuntimeException failure) {
                log.warn("Daily cleanup polling failed", failure);
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, DataCleanupScheduler.class, "poll()");
        }
    }
}
