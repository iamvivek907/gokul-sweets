package com.gokulsweets.restaurant.inventory.rolling;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.automation.config.InventoryAutomationProperties;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRule;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.repository.InventoryAutomationRuleRepository;
import com.gokulsweets.restaurant.inventory.automation.scheduler.InventoryAutomationScheduler;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

/** Verifies the legacy scheduler yields ownership without disabling manual generation APIs. */
class RollingOwnershipTest {
    @Test
    void legacySchedulerSkipsOnlyOptedInBranchesAndResumesWhenDisabled() {
        var jdbc = mock(JdbcTemplate.class);
        var tx = mock(TransactionTemplate.class);
        doAnswer(
                        call -> {
                            ((Consumer<TransactionStatus>) call.getArgument(0))
                                    .accept(mock(TransactionStatus.class));
                            return null;
                        })
                .when(tx)
                .executeWithoutResult(any());
        var rolling = mock(RollingScheduleService.class);
        var rules = mock(InventoryAutomationRuleRepository.class);
        var old = mock(InventoryAutomationGenerationService.class);
        var rule = mock(InventoryAutomationRule.class, RETURNS_DEEP_STUBS);
        when(rule.getBranchProduct().getBranch().getId()).thenReturn(12L);
        when(rules.findByActiveTrue()).thenReturn(List.of(rule));
        when(rolling.ownsInventory(12L)).thenReturn(true);
        var scheduler =
                new InventoryAutomationScheduler(
                        jdbc,
                        tx,
                        rolling,
                        rules,
                        old,
                        new InventoryAutomationProperties(),
                        Clock.systemUTC(),
                        new EnhancementProperties());
        scheduler.generateDailyAllocations();
        verifyNoInteractions(old);
        when(rolling.ownsInventory(12L)).thenReturn(false);
        scheduler.generateDailyAllocations();
        verify(old)
                .generate(
                        eq(12L),
                        any(),
                        any(),
                        eq(InventoryAutomationTrigger.SCHEDULED),
                        anyString());
    }
}
