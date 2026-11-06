package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily reconciliation, isolated from dedicated import workers. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "inventory.rolling",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RollingScheduleWorker {

    private final JdbcTemplate jdbc;

    private final RollingScheduleService service;

    @Value("${gokul.jobs.worker-enabled:false}")
    private boolean importWorker;

    /**
     * Maintains the calendar window once daily at midnight IST; admins can run checks separately.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void reconcile() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleWorker.class, "reconcile()");
        try {
            if (importWorker) return;
            for (Long branch :
                    jdbc.queryForList(
                            "SELECT branch_id FROM rolling_branch_schedules WHERE enabled ORDER BY"
                                    + " branch_id",
                            Long.class)) {
                try {
                    service.reconcile(branch);
                } catch (RuntimeException error) {
                    log.error("Rolling automation failed for branch {}", branch, error);
                }
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleWorker.class, "reconcile()");
        }
    }
}
