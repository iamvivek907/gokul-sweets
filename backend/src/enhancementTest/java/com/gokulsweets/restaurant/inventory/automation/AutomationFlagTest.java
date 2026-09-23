package com.gokulsweets.restaurant.inventory.automation;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.automation.config.InventoryAutomationProperties;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRule;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.repository.InventoryAutomationRuleRepository;
import com.gokulsweets.restaurant.inventory.automation.scheduler.InventoryAutomationScheduler;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;

class AutomationFlagTest {
    @Test void flagOffKeepsExistingSchedulerRangeAndOnBoundsRollingHorizon() {
        var repository = mock(InventoryAutomationRuleRepository.class);
        var generation = mock(InventoryAutomationGenerationService.class);
        var properties = new InventoryAutomationProperties();
        var features = new EnhancementProperties();
        var clock = Clock.fixed(Instant.parse("2026-09-22T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
        var branch = new Branch(); branch.setId(1L);
        var product = new BranchProduct(); product.setBranch(branch);
        var rule = new InventoryAutomationRule(); rule.setBranchProduct(product);
        when(repository.findByActiveTrue()).thenReturn(List.of(rule));
        var scheduler = new InventoryAutomationScheduler(repository, generation, properties, clock, features);
        scheduler.generateDailyAllocations();
        verify(generation).generate(1L, LocalDate.now(clock), LocalDate.now(clock).plusDays(59),
                InventoryAutomationTrigger.SCHEDULED, properties.getSystemActor());
        features.setInventoryAutomationV2(true);
        scheduler.generateDailyAllocations();
        verify(generation).generate(1L, LocalDate.now(clock), LocalDate.now(clock).plusDays(30),
                InventoryAutomationTrigger.SCHEDULED, properties.getSystemActor());
    }
}
