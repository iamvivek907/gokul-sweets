package com.gokulsweets.restaurant.inventory.rolling;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

/** Automatic retries recover failed branches without stopping other branch schedules. */
class RollingScheduleWorkerTest {
    @Test
    void retriesFailureOnNextScheduledPollAndContinuesOtherBranches() {
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
                .reconcile(1L);
        var worker = new RollingScheduleWorker(jdbc, service);
        worker.reconcile();
        worker.reconcile();
        var calls = inOrder(service);
        calls.verify(service).reconcile(1L);
        calls.verify(service).reconcile(2L);
        calls.verify(service).reconcile(1L);
        calls.verify(service).reconcile(2L);
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
