package com.gokulsweets.restaurant.inventory.rolling;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

/** Daily scheduling and branch isolation for automatic coverage. */
class RollingScheduleWorkerTest {
    @Test
    void schedulesOnceAtMidnightIndiaTimeWithoutStartupOrIntervalRuns() throws Exception {
        var scheduled =
                RollingScheduleWorker.class
                        .getMethod("reconcile")
                        .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        org.assertj.core.api.Assertions.assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        org.assertj.core.api.Assertions.assertThat(scheduled.zone()).isEqualTo("Asia/Kolkata");
        org.assertj.core.api.Assertions.assertThat(scheduled.fixedDelayString()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(scheduled.fixedRateString()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(scheduled.initialDelayString()).isEmpty();
        var cron = org.springframework.scheduling.support.CronExpression.parse(scheduled.cron());
        var before =
                java.time.ZonedDateTime.of(
                        2026, 12, 31, 23, 59, 59, 0, java.time.ZoneId.of(scheduled.zone()));
        var next = cron.next(before);
        org.assertj.core.api.Assertions.assertThat(next).isEqualTo(before.plusSeconds(1));
        org.assertj.core.api.Assertions.assertThat(cron.next(next)).isEqualTo(next.plusDays(1));
    }

    @Test
    void retriesFailureOnNextDailyRunAndContinuesOtherBranches() {
        var jdbc = mock(JdbcTemplate.class);
        var service = mock(RollingScheduleService.class);
        when(jdbc.queryForList(
                        "SELECT branch_id FROM rolling_branch_schedules WHERE enabled ORDER BY"
                                + " branch_id",
                        Long.class))
                .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Temporary database failure"))
                .doNothing()
                .when(service)
                .reconcile(1L, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
        var worker = new RollingScheduleWorker(jdbc, service);
        worker.reconcile();
        worker.reconcile();
        var calls = inOrder(service);
        calls.verify(service).reconcile(1L, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
        calls.verify(service).reconcile(2L, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
        calls.verify(service).reconcile(1L, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
        calls.verify(service).reconcile(2L, "SCHEDULED", "SYSTEM_ROLLING_AUTOMATION");
        verifyNoMoreInteractions(service);
    }

    @Test
    void dedicatedImportWorkerDoesNotRunDailyAutomation() {
        var jdbc = mock(JdbcTemplate.class);
        var service = mock(RollingScheduleService.class);
        var worker = new RollingScheduleWorker(jdbc, service);
        ReflectionTestUtils.setField(worker, "importWorker", true);
        worker.reconcile();
        verifyNoInteractions(jdbc, service);
    }
}
