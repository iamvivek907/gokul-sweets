package com.gokulsweets.restaurant.inventory.centre;

import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import tools.jackson.databind.ObjectMapper;

class InventoryIdlePollingTest {
    @Test
    void emptyQueueBacksOffButCommittedWorkAndExplicitRecoveryRunImmediately() {
        var jdbc = mock(JdbcTemplate.class);
        var worker =
                new InventoryCentreWorker(
                        jdbc,
                        mock(PlatformTransactionManager.class),
                        mock(InventoryCentreProcessor.class),
                        mock(StaffUserRepository.class),
                        mock(StaffUserDetailsService.class),
                        new ObjectMapper());
        worker.scheduledBatch();
        worker.scheduledBatch();
        verify(jdbc, times(1)).queryForList(anyString());
        worker.wake(new InventoryWorkQueued());
        worker.scheduledBatch();
        verify(jdbc, times(2)).queryForList(anyString());
        worker.runBatch();
        verify(jdbc, times(3)).queryForList(anyString());
    }
}
