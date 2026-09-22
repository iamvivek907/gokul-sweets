package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.config.StorefrontFeaturesController;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.order.controller.CartAvailabilityController;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.pickup.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartOrderingRulesTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    private final EnhancementProperties features = new EnhancementProperties();
    private final BranchPickupSettingsRepository settings = mock(BranchPickupSettingsRepository.class);
    private final SmartOrderingRules rules = new SmartOrderingRules(features, settings, clock);

    @Test void allFlagsDefaultOffAndSelectionRequiresAvailability() {
        var controller = new StorefrontFeaturesController(features, clock);
        assertThat(controller.features()).isEqualTo(new StorefrontFeaturesController.Features(false, false, false, false, false, 30, LocalDate.of(2026, 9, 22)));
        features.setSmartPickupSelection(true);
        assertThat(controller.features().smartPickupSelection()).isFalse();
        features.setSmartAvailability(true);
        assertThat(controller.features().smartPickupSelection()).isTrue();
    }

    @Test void flagOffLeavesLegacyWindowValidationAlone() {
        rules.validateWindow(slot(LocalDate.of(2025, 1, 1), LocalTime.NOON));
        verifyNoInteractions(settings);
        var service = mock(CartAvailabilityService.class);
        var controller = new CartAvailabilityController(features, service);
        assertThat(controller.check(1L, new CartAvailabilityController.Request(LocalDate.now(clock), 1,
                List.of(new CreateOrderItemRequest(1L, 1, null)), null)).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(service);
    }

    @Test void independentlyEnabledFlagsDoNotEnableOtherEnhancements() {
        for (int selected = 0; selected < 5; selected++) {
            var independent = new EnhancementProperties();
            independent.setSmartAvailability(selected == 0);
            independent.setSmartPickupSelection(selected == 1);
            independent.setInventoryAutomationV2(selected == 2);
            independent.setCustomerHomeV2(selected == 3);
            independent.setHomepageCampaigns(selected == 4);
            var exposed = new StorefrontFeaturesController(independent, clock).features();
            assertThat(exposed.smartAvailability()).isEqualTo(selected == 0);
            assertThat(exposed.smartPickupSelection()).isFalse();
            assertThat(exposed.inventoryAutomationV2()).isEqualTo(selected == 2);
            assertThat(exposed.customerHomeV2()).isEqualTo(selected == 3);
            assertThat(exposed.homepageCampaigns()).isEqualTo(selected == 4);
            new SmartOrderingRules(independent, settings, clock).validateWindow(
                    slot(LocalDate.now(clock).plusDays(1), LocalTime.NOON));
        }
        verify(settings, times(1)).findByBranchId(1L);
    }

    @Test void horizonIsInclusiveAndUsesShorterBranchLimit() {
        var settings = new BranchPickupSettings();
        settings.setAdvanceBookingDays(7);
        assertThat(rules.windowReason(slot(LocalDate.now(clock).plusDays(7), LocalTime.NOON), settings)).isNull();
        assertThat(rules.windowReason(slot(LocalDate.now(clock).plusDays(8), LocalTime.NOON), settings)).contains("window");
        settings.setAdvanceBookingDays(60);
        assertThat(rules.windowReason(slot(LocalDate.now(clock).plusDays(31), LocalTime.NOON), settings)).contains("window");
        settings.setEnabled(false);
        assertThat(rules.windowReason(slot(LocalDate.now(clock), LocalTime.NOON), settings)).contains("closed");
    }

    @Test void preparationAndExpectedReadyBoundariesMatch() {
        var policy = new BranchInventoryPolicy();
        policy.setBookingHorizonDays(30);
        policy.setProductionLeadMinutes(120);
        var allocation = new InventoryDailyAllocation();
        assertThat(rules.preparationReason(slot(LocalDate.now(clock), LocalTime.NOON), policy, allocation)).isNull();
        assertThat(rules.preparationReason(slot(LocalDate.now(clock), LocalTime.of(11, 59)), policy, allocation)).contains("preparation");
        allocation.setExpectedReadyAt(LocalDate.now(clock).atTime(13, 0));
        assertThat(rules.preparationReason(slot(LocalDate.now(clock), LocalTime.NOON), policy, allocation)).contains("ready later");
        assertThat(rules.preparationReason(slot(LocalDate.now(clock), LocalTime.of(13, 0)), policy, allocation)).isNull();
    }

    @Test void leadTimeCrossesIndiaMidnightWithoutAddingAWholeDay() {
        var midnightClock = Clock.fixed(Instant.parse("2026-09-22T18:25:00Z"), ZoneId.of("Asia/Kolkata"));
        var midnightRules = new SmartOrderingRules(features, settings, midnightClock);
        var policy = new BranchInventoryPolicy(); policy.setBookingHorizonDays(3); policy.setProductionLeadMinutes(15);
        assertThat(midnightRules.preparationReason(slot(LocalDate.of(2026, 9, 23), LocalTime.of(0, 9)), policy, null)).contains("preparation");
        assertThat(midnightRules.preparationReason(slot(LocalDate.of(2026, 9, 23), LocalTime.of(0, 10)), policy, null)).isNull();
        var allocation = new InventoryDailyAllocation();
        allocation.setExpectedReadyAt(LocalDateTime.of(2026, 9, 23, 0, 30));
        assertThat(midnightRules.preparationReason(slot(LocalDate.of(2026, 9, 23), LocalTime.of(0, 29)), policy, allocation)).contains("ready later");
        assertThat(midnightRules.preparationReason(slot(LocalDate.of(2026, 9, 23), LocalTime.of(0, 30)), policy, allocation)).isNull();
    }

    static PickupSlot slot(LocalDate date, LocalTime time) {
        var branch = new Branch(); branch.setId(1L); branch.setActive(true);
        var slot = new PickupSlot(); slot.setId(1L); slot.setBranch(branch);
        slot.setActive(true); slot.setSlotDate(date); slot.setStartTime(time); slot.setEndTime(time.plusMinutes(30));
        slot.setCapacity(5); slot.setBookedCount(0); slot.setPriorityCapacity(0); slot.setPriorityBookedCount(0);
        return slot;
    }
}
