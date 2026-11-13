package com.gokulsweets.restaurant.inventory.automation.scheduler;

import com.gokulsweets.restaurant.inventory.automation.config.InventoryAutomationProperties;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.repository.InventoryAutomationRuleRepository;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Backend inventory automation scheduler contract and implementation. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "inventory.automation",
        name = "scheduler-enabled",
        havingValue = "true")
@Slf4j
public class InventoryAutomationScheduler {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final org.springframework.jdbc.core.JdbcTemplate rollingJdbc;
    private final org.springframework.transaction.support.TransactionTemplate rollingTransactions;
    private final com.gokulsweets.restaurant.inventory.rolling.RollingScheduleService
            rollingSchedules;

    private final InventoryAutomationRuleRepository ruleRepository;

    private final InventoryAutomationGenerationService generationService;

    private final InventoryAutomationProperties properties;

    private final Clock inventoryClock;

    private final com.gokulsweets.restaurant.config.EnhancementProperties features;

    /** Generates daily allocations. */
    @Scheduled(
            cron = "${inventory.automation.cron:0 15 1 * * *}",
            zone = "${inventory.business-zone:Asia/Kolkata}")
    public void generateDailyAllocations() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryAutomationScheduler.class, "generateDailyAllocations()");
        try {
            if (dedicatedImportWorker) return;
            LocalDate today = LocalDate.now(inventoryClock);
            ruleRepository.findByActiveTrue().stream()
                    .map(rule -> rule.getBranchProduct().getBranch().getId())
                    .distinct()
                    .forEach(
                            branchId -> {
                                try {
                                    rollingTransactions.executeWithoutResult(
                                            state -> {
                                                rollingJdbc.query(
                                                        "SELECT id FROM branches WHERE id=? FOR"
                                                                + " UPDATE",
                                                        rs -> {
                                                            while (rs.next()) {
                                                                /* Acquire branch ownership lock. */
                                                            }
                                                            return null;
                                                        },
                                                        branchId);
                                                if (rollingSchedules.ownsInventory(branchId))
                                                    return;
                                                generationService.generate(
                                                        branchId,
                                                        today,
                                                        today.plusDays(
                                                                features.isInventoryAutomationV2()
                                                                        ? Math.min(
                                                                                features
                                                                                        .getFutureOrderingDays(),
                                                                                properties
                                                                                                .getMaximumRunDays()
                                                                                        - 1L)
                                                                        : properties
                                                                                        .getMaximumRunDays()
                                                                                - 1L),
                                                        InventoryAutomationTrigger.SCHEDULED,
                                                        properties.getSystemActor());
                                            });
                                } catch (Exception exception) {
                                    log.error(
                                            "Scheduled inventory automation failed: branchId={}",
                                            branchId,
                                            exception);
                                }
                            });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryAutomationScheduler.class,
                    "generateDailyAllocations()");
        }
    }
}
