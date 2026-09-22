package com.gokulsweets.restaurant.inventory.automation.scheduler;

import com.gokulsweets.restaurant.inventory.automation.config.InventoryAutomationProperties;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.repository.InventoryAutomationRuleRepository;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "inventory.automation",
        name = "scheduler-enabled",
        havingValue = "true"
)
@Slf4j
public class InventoryAutomationScheduler {
    private final InventoryAutomationRuleRepository ruleRepository;
    private final InventoryAutomationGenerationService generationService;
    private final InventoryAutomationProperties properties;
    private final Clock inventoryClock;
    private final com.gokulsweets.restaurant.config.EnhancementProperties features;

    @Scheduled(
            cron = "${inventory.automation.cron:0 15 1 * * *}",
            zone = "${inventory.business-zone:Asia/Kolkata}"
    )
    public void generateDailyAllocations() {
        LocalDate today = LocalDate.now(inventoryClock);
        ruleRepository.findByActiveTrue().stream()
                .map(rule -> rule.getBranchProduct().getBranch().getId())
                .distinct()
                .forEach(branchId -> {
                    try {
                        generationService.generate(
                                branchId,
                                today,
                                today.plusDays(features.isInventoryAutomationV2()
                                        ? Math.min(features.getFutureOrderingDays(), properties.getMaximumRunDays() - 1L)
                                        : properties.getMaximumRunDays() - 1L),
                                InventoryAutomationTrigger.SCHEDULED,
                                properties.getSystemActor()
                        );
                    } catch (Exception exception) {
                        log.error("Scheduled inventory automation failed: branchId={}", branchId, exception);
                    }
                });
    }
}
