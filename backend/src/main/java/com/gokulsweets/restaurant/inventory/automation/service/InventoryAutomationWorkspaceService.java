package com.gokulsweets.restaurant.inventory.automation.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.inventory.automation.dto.*;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRule;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAvailabilityWindow;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationMode;
import com.gokulsweets.restaurant.inventory.automation.enums.InventorySeasonalMode;
import com.gokulsweets.restaurant.inventory.automation.repository.*;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryQuantityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAutomationWorkspaceService {
    private final BranchRepository branchRepository;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryAutomationRuleRepository ruleRepository;
    private final InventoryAvailabilityWindowRepository windowRepository;
    private final InventoryAutomationRunRepository runRepository;
    private final InventoryQuantityService quantityService;

    @Transactional(readOnly = true)
    public AutomationWorkspaceResponse getWorkspace(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "BRANCH_NOT_FOUND",
                        "Selected branch does not exist."
                ));
        if (!branch.isActive()) {
            throw new InventoryConflictException(
                    "BRANCH_INACTIVE",
                    "Inventory automation is unavailable for an inactive branch."
            );
        }

        List<BranchInventoryPolicy> policies = policyRepository
                .findByBranchProduct_Branch_IdAndOnlineEnabledTrueOrderByBranchProduct_Product_NameAsc(branchId);
        Map<Long, InventoryAutomationRule> rules = ruleRepository
                .findByBranchProduct_Branch_IdOrderByBranchProduct_Product_NameAsc(branchId)
                .stream()
                .collect(Collectors.toMap(
                        rule -> rule.getBranchProduct().getId(),
                        Function.identity()
                ));
        List<Long> ruleIds = rules.values().stream().map(InventoryAutomationRule::getId).toList();
        Map<Long, List<InventoryAvailabilityWindow>> windows = ruleIds.isEmpty()
                ? Map.of()
                : windowRepository.findByAutomationRuleIdInAndActiveTrue(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(window -> window.getAutomationRule().getId()));

        List<AutomationRuleResponse> responses = policies.stream()
                .map(policy -> toResponse(
                        policy,
                        rules.get(policy.getBranchProduct().getId()),
                        windows
                ))
                .toList();

        return new AutomationWorkspaceResponse(
                branchId,
                responses,
                runRepository.findTop20ByBranchIdOrderByStartedAtDesc(branchId)
                        .stream()
                        .map(InventoryAutomationMapper::toRunResponse)
                        .toList()
        );
    }

    @Transactional
    public AutomationRuleResponse updateRule(
            Long branchId,
            Long branchProductId,
            AutomationRuleUpdateRequest request
    ) {
        BranchInventoryPolicy policy = policyRepository.findByBranchProductId(branchProductId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "INVENTORY_POLICY_NOT_FOUND",
                        "Configure an inventory policy before enabling automation."
                ));
        if (!policy.getBranchProduct().getBranch().getId().equals(branchId)) {
            throw new InventoryConflictException(
                    "BRANCH_PRODUCT_MISMATCH",
                    "The selected product does not belong to this branch."
            );
        }

        BigDecimal guaranteed = quantityService.normalizeNonNegative(
                request.guaranteedQuantity(), policy.getInventoryUnit(), "Guaranteed quantity"
        );
        BigDecimal maximum = quantityService.normalizeNonNegative(
                request.maximumSuggestedQuantity(), policy.getInventoryUnit(), "Maximum suggestion"
        );
        if (request.automationMode() == InventoryAutomationMode.AUTO_APPROVE_GUARANTEED
                && guaranteed.signum() <= 0) {
            throw new InventoryConflictException(
                    "GUARANTEED_QUANTITY_REQUIRED",
                    "Auto approval requires a guaranteed quantity greater than zero."
            );
        }
        if (policy.getMaximumDailyAllocation() != null
                && guaranteed.compareTo(policy.getMaximumDailyAllocation()) > 0) {
            throw new InventoryConflictException(
                    "GUARANTEED_QUANTITY_EXCEEDS_POLICY",
                    "Guaranteed quantity cannot exceed the policy maximum."
            );
        }
        validateWindows(request);

        InventoryAutomationRule rule = ruleRepository.findByBranchProductId(branchProductId)
                .orElseGet(InventoryAutomationRule::new);
        rule.setBranchProduct(policy.getBranchProduct());
        rule.setAutomationMode(request.automationMode());
        rule.setGuaranteedQuantity(guaranteed);
        rule.setForecastEnabled(request.forecastEnabled());
        rule.setLookbackWeeks(request.lookbackWeeks());
        rule.setMinimumHistoryDays(request.minimumHistoryDays());
        rule.setDemandMultiplier(request.demandMultiplier());
        rule.setMaximumSuggestedQuantity(maximum);
        rule.setAvailableDaysMask(request.availableDaysMask());
        rule.setSeasonalMode(request.seasonalMode());
        rule.setGenerationHorizonDays(request.generationHorizonDays());
        rule.setActive(request.active());
        InventoryAutomationRule saved = ruleRepository.saveAndFlush(rule);

        windowRepository.deleteByAutomationRuleId(saved.getId());
        List<InventoryAvailabilityWindow> savedWindows = Optional.ofNullable(request.windows())
                .orElse(List.of())
                .stream()
                .map(value -> {
                    InventoryAvailabilityWindow window = new InventoryAvailabilityWindow();
                    window.setAutomationRule(saved);
                    window.setName(value.name().trim());
                    window.setStartDate(value.startDate());
                    window.setEndDate(value.endDate());
                    window.setActive(value.active());
                    return window;
                })
                .map(windowRepository::save)
                .toList();

        return toResponse(policy, saved, Map.of(saved.getId(), savedWindows));
    }

    private void validateWindows(AutomationRuleUpdateRequest request) {
        List<AvailabilityWindowRequest> windows = Optional.ofNullable(request.windows()).orElse(List.of());
        for (AvailabilityWindowRequest window : windows) {
            if (window.endDate().isBefore(window.startDate())) {
                throw new InventoryConflictException(
                        "INVALID_AVAILABILITY_WINDOW",
                        "Availability window end date cannot be before its start date."
                );
            }
        }
        if (request.seasonalMode() == InventorySeasonalMode.WINDOW_ONLY
                && windows.stream().noneMatch(AvailabilityWindowRequest::active)) {
            throw new InventoryConflictException(
                    "ACTIVE_WINDOW_REQUIRED",
                    "Festival or seasonal products require at least one active availability window."
            );
        }
    }

    private AutomationRuleResponse toResponse(
            BranchInventoryPolicy policy,
            InventoryAutomationRule rule,
            Map<Long, List<InventoryAvailabilityWindow>> windowsByRule
    ) {
        boolean missing = rule == null;
        Long ruleId = missing ? null : rule.getId();
        return new AutomationRuleResponse(
                policy.getBranchProduct().getId(),
                policy.getBranchProduct().getProduct().getId(),
                policy.getBranchProduct().getProduct().getCode(),
                policy.getBranchProduct().getProduct().getName(),
                policy.getBranchProduct().getProduct().getCategory().getName(),
                policy.getInventoryUnit().name(),
                policy.isOnlineEnabled(),
                missing ? InventoryAutomationMode.CREATE_DRAFT.name() : rule.getAutomationMode().name(),
                missing ? BigDecimal.ZERO : rule.getGuaranteedQuantity(),
                missing || rule.isForecastEnabled(),
                missing ? 8 : rule.getLookbackWeeks(),
                missing ? 3 : rule.getMinimumHistoryDays(),
                missing ? BigDecimal.ONE : rule.getDemandMultiplier(),
                missing ? policy.getMaximumDailyAllocation() : rule.getMaximumSuggestedQuantity(),
                missing ? 127 : rule.getAvailableDaysMask(),
                missing ? InventorySeasonalMode.ALWAYS.name() : rule.getSeasonalMode().name(),
                missing ? null : rule.getGenerationHorizonDays(),
                !missing && rule.isActive(),
                ruleId == null ? List.of() : windowsByRule.getOrDefault(ruleId, List.of())
                        .stream()
                        .map(window -> new AutomationRuleResponse.Window(
                                window.getId(), window.getName(), window.getStartDate(),
                                window.getEndDate(), window.isActive()
                        ))
                        .toList()
        );
    }
}
