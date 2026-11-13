package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Daily reconciliation, isolated from dedicated import workers. */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "inventory.rolling",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RollingScheduleWorker {

    private final JdbcTemplate jdbc;

    private final RollingScheduleService service;

    private final Executor rollingScheduleExecutor;

    public RollingScheduleWorker(
            JdbcTemplate jdbc,
            RollingScheduleService service,
            @Qualifier("rollingScheduleExecutor") Executor rollingScheduleExecutor) {
        this.jdbc = jdbc;
        this.service = service;
        this.rollingScheduleExecutor = rollingScheduleExecutor;
    }

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
            try {
                rollingScheduleExecutor.execute(this::reconcileBranches);
            } catch (RejectedExecutionException rejected) {
                log.warn(
                        "Daily rolling automation could not start because its executor is busy or"
                                + " stopping.");
            }
        } finally {
            MethodTiming.finish(started, RollingScheduleWorker.class, "reconcile()");
        }
    }

    /** Reconciles branches serially on the dedicated rolling automation thread. */
    private void reconcileBranches() {
        final long started = MethodTiming.start(RollingScheduleWorker.class, "reconcileBranches()");
        try {
            for (Long branch :
                    jdbc.queryForList(
                            "SELECT branch_id FROM rolling_branch_schedules WHERE enabled ORDER BY"
                                    + " branch_id",
                            Long.class)) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    service.reconcile(branch, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
                } catch (RuntimeException error) {
                    log.error("Rolling automation failed for branch {}", branch, error);
                }
            }
        } catch (RuntimeException error) {
            log.error("Could not load branches for daily rolling automation", error);
        } finally {
            MethodTiming.finish(started, RollingScheduleWorker.class, "reconcileBranches()");
        }
    }
}
