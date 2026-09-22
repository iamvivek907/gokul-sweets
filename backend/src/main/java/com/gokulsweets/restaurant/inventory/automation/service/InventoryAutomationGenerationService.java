package com.gokulsweets.restaurant.inventory.automation.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.inventory.automation.config.InventoryAutomationProperties;
import com.gokulsweets.restaurant.inventory.automation.dto.AutomationRunResponse;
import com.gokulsweets.restaurant.inventory.automation.entity.*;
import com.gokulsweets.restaurant.inventory.automation.enums.*;
import com.gokulsweets.restaurant.inventory.automation.repository.*;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryLedgerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAutomationGenerationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

    private final EntityManager entityManager;
    private final InventoryAutomationRuleRepository ruleRepository;
    private final InventoryAvailabilityWindowRepository windowRepository;
    private final InventoryAutomationRunRepository runRepository;
    private final InventoryAutomationRunItemRepository itemRepository;
    private final InventoryAutomationManagedAllocationRepository managedRepository;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryLedgerService ledgerService;
    private final InventoryAutomationProperties properties;
    private final Clock inventoryClock;
    private final com.gokulsweets.restaurant.config.EnhancementProperties features;

    @Transactional
    public AutomationRunResponse generate(
            Long branchId,
            LocalDate fromDate,
            LocalDate throughDate,
            InventoryAutomationTrigger trigger,
            String actor
    ) {
        validateRange(fromDate, throughDate);
        Branch branch = entityManager.find(Branch.class, branchId, LockModeType.PESSIMISTIC_WRITE);
        if (branch == null) {
            throw new InventoryNotFoundException("BRANCH_NOT_FOUND", "Selected branch does not exist.");
        }
        if (!branch.isActive()) {
            throw new InventoryConflictException(
                    "BRANCH_INACTIVE",
                    "Inventory automation cannot run for an inactive branch."
            );
        }

        LocalDate today = LocalDate.now(inventoryClock);
        List<InventoryAutomationRule> rules = ruleRepository
                .findByBranchProduct_Branch_IdOrderByBranchProduct_Product_NameAsc(branchId)
                .stream()
                .filter(InventoryAutomationRule::isActive)
                .toList();
        List<Long> branchProductIds = rules.stream()
                .map(rule -> rule.getBranchProduct().getId())
                .toList();
        Map<Long, BranchInventoryPolicy> policies = policyRepository
                .findByBranchProductIdIn(branchProductIds)
                .stream()
                .collect(Collectors.toMap(
                        policy -> policy.getBranchProduct().getId(), Function.identity()
                ));
        Map<Long, List<InventoryAvailabilityWindow>> windows = rules.isEmpty()
                ? Map.of()
                : windowRepository.findByAutomationRuleIdInAndActiveTrue(
                        rules.stream().map(InventoryAutomationRule::getId).toList()
                ).stream().collect(Collectors.groupingBy(window -> window.getAutomationRule().getId()));

        int maximumLookback = rules.stream()
                .mapToInt(InventoryAutomationRule::getLookbackWeeks)
                .max().orElse(1);
        List<InventoryDailyAllocation> history = allocationRepository
                .findByBranchProduct_Branch_IdAndServiceDateBetweenOrderByServiceDateAsc(
                        branchId, today.minusWeeks(maximumLookback), today.minusDays(1)
                );
        Map<HistoryKey, List<InventoryDailyAllocation>> historyByProductAndDay = history
                .stream()
                .collect(Collectors.groupingBy(value -> new HistoryKey(
                        value.getBranchProduct().getId(),
                        value.getServiceDate().getDayOfWeek()
                )));
        Map<Key, InventoryDailyAllocation> targets = allocationRepository
                .findByBranchProduct_Branch_IdAndServiceDateBetweenOrderByServiceDateAsc(
                        branchId, fromDate, throughDate
                ).stream().collect(Collectors.toMap(
                        allocation -> new Key(allocation.getBranchProduct().getId(), allocation.getServiceDate()),
                        Function.identity()
                ));
        if (features.isInventoryAutomationV2()) {
            // Match checkout's date/product lock order when regenerating a range; preserve concurrent reservations.
            targets.values().stream().filter(allocation -> branchProductIds.contains(allocation.getBranchProduct().getId()))
                    .sorted(Comparator.comparing(InventoryDailyAllocation::getServiceDate)
                            .thenComparing(allocation -> allocation.getBranchProduct().getId()))
                    .forEach(allocation -> entityManager.refresh(allocation, LockModeType.PESSIMISTIC_WRITE));
        }
        List<Long> targetIds = targets.values().stream()
                .map(InventoryDailyAllocation::getId)
                .toList();
        Map<Long, InventoryAutomationManagedAllocation> managedByAllocationId = targetIds.isEmpty()
                ? new HashMap<>()
                : managedRepository.findByAllocationIdIn(targetIds)
                .stream()
                .collect(Collectors.toMap(
                        value -> value.getAllocation().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        HashMap::new
                ));

        InventoryAutomationRun run = new InventoryAutomationRun();
        run.setBranch(branch);
        run.setFromDate(fromDate);
        run.setThroughDate(throughDate);
        run.setTriggerType(trigger);
        run.setRunStatus(InventoryAutomationRunStatus.RUNNING);
        run.setInitiatedBy(actor);
        run.setStartedAt(LocalDateTime.now(inventoryClock));
        run = runRepository.saveAndFlush(run);

        for (InventoryAutomationRule rule : rules) {
            BranchInventoryPolicy policy = policies.get(rule.getBranchProduct().getId());
            if (policy == null || !policy.isOnlineEnabled()
                    || policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY) {
                addItem(run, rule, fromDate, InventoryAutomationOutcome.SKIPPED,
                        null, null, "Online physical inventory policy is unavailable.");
                incrementSkipped(run);
                continue;
            }

            LocalDate ruleThrough = permittedThroughDate(rule, policy, today, throughDate);
            for (LocalDate date = fromDate; !date.isAfter(ruleThrough); date = date.plusDays(1)) {
                processDate(run, rule, policy, windows.getOrDefault(rule.getId(), List.of()),
                        date, today, historyByProductAndDay, targets, managedByAllocationId);
            }
            if (features.isInventoryAutomationV2() && ruleThrough.isBefore(throughDate)) {
                addItem(run, rule, fromDate.isAfter(ruleThrough) ? fromDate : ruleThrough.plusDays(1),
                        InventoryAutomationOutcome.SKIPPED, null, null,
                        "Remaining dates through " + throughDate + " were not generated: the product, rule or global planning window ends on "
                                + ruleThrough + ". Review the configured ordering and planning windows.");
                incrementSkipped(run);
            }
        }

        run.setRunStatus(run.getErrorCount() > 0
                ? InventoryAutomationRunStatus.PARTIAL
                : InventoryAutomationRunStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now(inventoryClock));
        InventoryAutomationRun saved = runRepository.save(run);
        log.info("Inventory automation completed: branchId={}, fromDate={}, throughDate={}, created={}, updated={}, suggested={}, skipped={}, errors={}",
                branchId, fromDate, throughDate, saved.getCreatedCount(), saved.getUpdatedCount(),
                saved.getSuggestedCount(), saved.getSkippedCount(), saved.getErrorCount());
        return InventoryAutomationMapper.toRunResponse(saved);
    }

    private void processDate(
            InventoryAutomationRun run,
            InventoryAutomationRule rule,
            BranchInventoryPolicy policy,
            List<InventoryAvailabilityWindow> windows,
            LocalDate date,
            LocalDate today,
            Map<HistoryKey, List<InventoryDailyAllocation>> historyByProductAndDay,
            Map<Key, InventoryDailyAllocation> targets,
            Map<Long, InventoryAutomationManagedAllocation> managedByAllocationId
    ) {
        if (!isEligibleDate(rule, windows, date)) {
            addItem(run, rule, date, InventoryAutomationOutcome.SKIPPED,
                    null, null, "Date is outside the configured selling schedule.");
            incrementSkipped(run);
            return;
        }

        Forecast forecast = calculateForecast(
                rule,
                policy,
                today,
                historyByProductAndDay.getOrDefault(
                        new HistoryKey(rule.getBranchProduct().getId(), date.getDayOfWeek()),
                        List.of()
                )
        );

        /*
         * Drafts may carry the forecasted quantity because an admin must
         * explicitly approve them before they become sellable.
         *
         * Automatic approval is deliberately restricted to the manually
         * configured guaranteed quantity. A forecast can therefore guide
         * production without silently exposing uncertain stock online.
         */
        BigDecimal guaranteed = normalize(
                cap(rule.getGuaranteedQuantity(), policy.getMaximumDailyAllocation()),
                policy.getInventoryUnit()
        );
        BigDecimal proposed = rule.getAutomationMode()
                == InventoryAutomationMode.AUTO_APPROVE_GUARANTEED
                ? guaranteed
                : forecast.quantity();

        if (rule.getAutomationMode() == InventoryAutomationMode.SUGGEST_ONLY) {
            addItem(run, rule, date, InventoryAutomationOutcome.SUGGESTED,
                    proposed, forecast.quantity(), "Forecast generated without changing allocation.");
            run.setSuggestedCount(run.getSuggestedCount() + 1);
            return;
        }

        Key key = new Key(rule.getBranchProduct().getId(), date);
        InventoryDailyAllocation allocation = targets.get(key);
        boolean created = allocation == null;
        if (!created && !canAutomationUpdate(allocation, managedByAllocationId)) {
            addItem(run, rule, date, InventoryAutomationOutcome.SKIPPED,
                    proposed, forecast.quantity(), "Existing allocation is manually managed or already has stock activity.");
            incrementSkipped(run);
            return;
        }

        if (created) {
            allocation = new InventoryDailyAllocation();
            allocation.setBranchProduct(rule.getBranchProduct());
            allocation.setServiceDate(date);
            allocation.setInventoryUnit(policy.getInventoryUnit());
        }

        BigDecimal previousApproved = allocation.getApprovedQuantity();
        allocation.setApprovedQuantity(proposed);
        allocation.setSafetyBufferQuantity(policy.getDefaultSafetyBuffer());
        allocation.setForecastQuantity(forecast.quantity());
        allocation.setForecastConfidence(forecast.confidence());
        allocation.setNote("Generated by inventory automation. Forecast is advisory.");

        if (rule.getAutomationMode() == InventoryAutomationMode.AUTO_APPROVE_GUARANTEED) {
            if (proposed.signum() <= 0) {
                addItem(run, rule, date, InventoryAutomationOutcome.ERROR,
                        proposed, forecast.quantity(), "Auto approval requires a positive guaranteed quantity.");
                run.setErrorCount(run.getErrorCount() + 1);
                return;
            }
            allocation.setStatus(InventoryAllocationStatus.APPROVED);
            allocation.setApprovedBy(properties.getSystemActor());
            allocation.setApprovedAt(LocalDateTime.now(inventoryClock));
        } else {
            allocation.setStatus(InventoryAllocationStatus.DRAFT);
            allocation.setApprovedBy(null);
            allocation.setApprovedAt(null);
        }

        InventoryDailyAllocation saved = allocationRepository.save(allocation);
        targets.put(key, saved);
        if (rule.getAutomationMode() == InventoryAutomationMode.AUTO_APPROVE_GUARANTEED) {
            BigDecimal delta = proposed.subtract(previousApproved);
            if (delta.signum() != 0) {
                ledgerService.record(saved, null, InventoryTransactionType.ALLOCATION_APPROVED,
                        delta, null, "automation:" + run.getId() + ":" + saved.getId(),
                        "Guaranteed online allocation generated automatically.", properties.getSystemActor());
            }
        }

        InventoryAutomationManagedAllocation managed = managedByAllocationId
                .getOrDefault(saved.getId(), new InventoryAutomationManagedAllocation());
        managed.setAllocation(saved);
        managed.setAutomationRule(rule);
        managed.setLastRun(run);
        managed.setLastAutomatedAt(LocalDateTime.now(inventoryClock));
        InventoryAutomationManagedAllocation savedManaged = managedRepository.save(managed);
        managedByAllocationId.put(saved.getId(), savedManaged);

        InventoryAutomationOutcome outcome = created
                ? InventoryAutomationOutcome.CREATED
                : InventoryAutomationOutcome.UPDATED;
        addItem(run, rule, date, outcome, proposed, forecast.quantity(),
                created ? "Allocation generated." : "Automation-managed allocation refreshed.");
        if (created) run.setCreatedCount(run.getCreatedCount() + 1);
        else run.setUpdatedCount(run.getUpdatedCount() + 1);
    }

    private boolean canAutomationUpdate(
            InventoryDailyAllocation allocation,
            Map<Long, InventoryAutomationManagedAllocation> managedByAllocationId
    ) {
        if (allocation.getId() == null) return true;
        if (!managedByAllocationId.containsKey(allocation.getId())) return false;
        if (hasActivity(allocation)) return false;
        if (allocation.getStatus() != InventoryAllocationStatus.DRAFT
                && allocation.getStatus() != InventoryAllocationStatus.APPROVED) return false;
        return allocation.getApprovedBy() == null
                || properties.getSystemActor().equals(allocation.getApprovedBy());
    }

    private boolean hasActivity(InventoryDailyAllocation value) {
        return value.getHeldQuantity().signum() > 0
                || value.getCommittedQuantity().signum() > 0
                || value.getReadyQuantity().signum() > 0
                || value.getFulfilledQuantity().signum() > 0
                || value.getWastedQuantity().signum() > 0;
    }

    private Forecast calculateForecast(
            InventoryAutomationRule rule,
            BranchInventoryPolicy policy,
            LocalDate today,
            List<InventoryDailyAllocation> comparableHistory
    ) {
        if (!rule.isForecastEnabled()) return new Forecast(ZERO, "DISABLED");
        LocalDate earliest = today.minusWeeks(rule.getLookbackWeeks());
        List<BigDecimal> samples = comparableHistory.stream()
                .filter(value -> !value.getServiceDate().isBefore(earliest))
                /*
                 * Fulfilled quantity remains recorded after pickup while
                 * committed quantity is also retained by the lifecycle
                 * module. Taking the greater value captures paid demand
                 * without counting a collected order twice.
                 *
                 * Cancelled/no-show quantities have already been removed
                 * from committed quantity, so they do not inflate demand.
                 */
                .map(value -> value.getCommittedQuantity()
                        .max(value.getFulfilledQuantity()))
                .toList();

        BigDecimal forecast;
        if (samples.size() < rule.getMinimumHistoryDays()) {
            forecast = rule.getGuaranteedQuantity();
        } else {
            BigDecimal total = samples.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            forecast = total.divide(BigDecimal.valueOf(samples.size()), 3, RoundingMode.HALF_UP)
                    .multiply(rule.getDemandMultiplier());
        }
        forecast = forecast.max(rule.getGuaranteedQuantity());
        forecast = cap(forecast, rule.getMaximumSuggestedQuantity());
        forecast = cap(forecast, policy.getMaximumDailyAllocation());
        forecast = normalize(forecast, policy.getInventoryUnit());
        String confidence = samples.size() >= 8 ? "HIGH" : samples.size() >= 4 ? "MEDIUM" : "LOW";
        return new Forecast(forecast, confidence);
    }

    private boolean isEligibleDate(
            InventoryAutomationRule rule,
            List<InventoryAvailabilityWindow> windows,
            LocalDate date
    ) {
        if (rule.getSeasonalMode() == InventorySeasonalMode.MANUAL_ONLY) return false;
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        if ((rule.getAvailableDaysMask() & bit) == 0) return false;
        if (rule.getSeasonalMode() == InventorySeasonalMode.ALWAYS) return true;
        return windows.stream().anyMatch(window -> window.isActive()
                && !date.isBefore(window.getStartDate())
                && !date.isAfter(window.getEndDate()));
    }

    private LocalDate permittedThroughDate(
            InventoryAutomationRule rule,
            BranchInventoryPolicy policy,
            LocalDate today,
            LocalDate requested
    ) {
        int horizon = rule.getGenerationHorizonDays() == null
                ? policy.getBookingHorizonDays()
                : Math.min(rule.getGenerationHorizonDays(), policy.getBookingHorizonDays());
        if (features.isInventoryAutomationV2()) horizon = Math.min(horizon, features.getFutureOrderingDays());
        return requested.isBefore(today.plusDays(horizon)) ? requested : today.plusDays(horizon);
    }

    private void validateRange(LocalDate fromDate, LocalDate throughDate) {
        LocalDate today = LocalDate.now(inventoryClock);
        if (fromDate == null || throughDate == null || fromDate.isBefore(today)
                || throughDate.isBefore(fromDate)) {
            throw new InventoryConflictException(
                    "INVALID_AUTOMATION_DATE_RANGE",
                    "Automation dates must start today or later and end on or after the start date."
            );
        }
        if (Duration.between(fromDate.atStartOfDay(), throughDate.plusDays(1).atStartOfDay()).toDays()
                > properties.getMaximumRunDays()) {
            throw new InventoryConflictException(
                    "AUTOMATION_RANGE_TOO_LARGE",
                    "Requested automation range exceeds the configured maximum."
            );
        }
    }

    private BigDecimal cap(BigDecimal value, BigDecimal maximum) {
        BigDecimal safe = value == null ? ZERO : value;
        return maximum == null ? safe : safe.min(maximum);
    }

    private BigDecimal normalize(BigDecimal value, InventoryUnit unit) {
        return unit == InventoryUnit.PIECE
                ? value.setScale(0, RoundingMode.CEILING).setScale(3)
                : value.setScale(3, RoundingMode.HALF_UP);
    }

    private void addItem(
            InventoryAutomationRun run,
            InventoryAutomationRule rule,
            LocalDate date,
            InventoryAutomationOutcome outcome,
            BigDecimal proposed,
            BigDecimal forecast,
            String message
    ) {
        InventoryAutomationRunItem item = new InventoryAutomationRunItem();
        item.setRun(run);
        item.setBranchProduct(rule.getBranchProduct());
        item.setServiceDate(date);
        item.setOutcome(outcome);
        item.setProposedQuantity(proposed);
        item.setForecastQuantity(forecast);
        item.setMessage(message);
        itemRepository.save(item);
    }

    private void incrementSkipped(InventoryAutomationRun run) {
        run.setSkippedCount(run.getSkippedCount() + 1);
    }

    private record Key(Long branchProductId, LocalDate serviceDate) {
    }

    private record HistoryKey(Long branchProductId, DayOfWeek dayOfWeek) {
    }

    private record Forecast(BigDecimal quantity, String confidence) {
    }
}
