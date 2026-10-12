package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Backend analytics refresh maintenance contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"gokul.reporting.automatic-refresh", "gokul.jobs.background-enabled"},
        havingValue = "true")
public class AnalyticsRefreshMaintenance {

    private final AnalyticsRefreshService service;

    /**
     * Refreshes analytics refresh maintenance data.
     *
     * <p>Delegates to {@code service.refreshChangedOrders(...)}.
     */
    @Scheduled(
            fixedDelayString = "${gokul.reporting.refresh-delay-ms:60000}",
            initialDelayString = "${gokul.reporting.initial-delay-ms:10000}")
    public void refresh() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AnalyticsRefreshMaintenance.class, "refresh()");
        try {
            try {
                service.refreshChangedOrders();
            } catch (Exception failure) {
                log.error(
                        "Automatic analytics refresh failed; previous report snapshot retained",
                        failure);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AnalyticsRefreshMaintenance.class, "refresh()");
        }
    }
}
