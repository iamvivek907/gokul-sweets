package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.BranchPickupSettings;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Backend smart ordering rules contract and implementation. */
@Service
@RequiredArgsConstructor
public class SmartOrderingRules {

    private final EnhancementProperties features;

    private final BranchPickupSettingsRepository settingsRepository;

    private final Clock inventoryClock;

    /**
     * Windows reason.
     *
     * @param slot the slot
     * @param settings the settings
     * @return the window reason result
     */
    public String windowReason(PickupSlot slot, BranchPickupSettings settings) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SmartOrderingRules.class, "windowReason(PickupSlot,BranchPickupSettings)");
        try {
            LocalDateTime now = LocalDateTime.now(inventoryClock);
            if (!slot.isActive() || !slot.getSlotDate().atTime(slot.getStartTime()).isAfter(now)) {
                return "This pickup time is no longer available.";
            }
            if (settings != null && !settings.isEnabled())
                return "Pickup is currently closed at this branch.";
            int days =
                    settings == null
                            ? features.getFutureOrderingDays()
                            : Math.min(
                                    features.getFutureOrderingDays(),
                                    settings.getAdvanceBookingDays());
            if (slot.getSlotDate().isAfter(now.toLocalDate().plusDays(days))) {
                return "This date is outside the branch's advance ordering window.";
            }
            // Opening hours are generation defaults; existing slots intentionally retain their own
            // schedule.
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SmartOrderingRules.class,
                    "windowReason(PickupSlot,BranchPickupSettings)");
        }
    }

    /**
     * Validates window.
     *
     * @param slot the slot
     */
    public void validateWindow(PickupSlot slot) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(SmartOrderingRules.class, "validateWindow(PickupSlot)");
        try {
            if (!features.isSmartAvailability()) return;
            String reason =
                    windowReason(
                            slot,
                            settingsRepository
                                    .findByBranchId(slot.getBranch().getId())
                                    .orElse(null));
            if (reason != null) throw new IllegalArgumentException(reason);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SmartOrderingRules.class,
                    "validateWindow(PickupSlot)");
        }
    }

    /**
     * Preparations reason.
     *
     * @param slot the slot
     * @param policy the policy
     * @param allocation the allocation
     * @return the preparation reason result
     */
    public String preparationReason(
            PickupSlot slot, BranchInventoryPolicy policy, InventoryDailyAllocation allocation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SmartOrderingRules.class,
                        "preparationReason(PickupSlot,BranchInventoryPolicy,InventoryDailyAllocation)");
        try {
            LocalDateTime now = LocalDateTime.now(inventoryClock);
            LocalDateTime pickupAt = slot.getSlotDate().atTime(slot.getStartTime());
            if (slot.getSlotDate()
                    .isAfter(now.toLocalDate().plusDays(policy.getBookingHorizonDays()))) {
                return "Choose an earlier date within this product's advance ordering window.";
            }
            if (pickupAt.isBefore(now.plusMinutes(policy.getProductionLeadMinutes()))) {
                return "This cart needs more preparation time. Choose a later pickup.";
            }
            if (allocation != null
                    && allocation.getExpectedReadyAt() != null
                    && pickupAt.isBefore(allocation.getExpectedReadyAt())) {
                return "Your items will be ready later. Choose a later pickup.";
            }
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SmartOrderingRules.class,
                    "preparationReason(PickupSlot,BranchInventoryPolicy,InventoryDailyAllocation)");
        }
    }
}
