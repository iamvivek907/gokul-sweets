package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupCommitmentCheckTest {
    private final LocalDate day = LocalDate.of(2026, 9, 26);
    private final EnhancementProperties flags = new EnhancementProperties();
    private final CartAvailabilityService availability = mock(CartAvailabilityService.class);
    private final PickupCommitmentCheck check = new PickupCommitmentCheck(flags, availability);
    private final List<CreateOrderItemRequest> cart = List.of(new CreateOrderItemRequest(11L, null, 250));

    @Test void offPathAndMissingDependencyNeverAddPreflight() {
        check.checkNewOrder(order(PickupType.NORMAL), cart);
        flags.setAuthoritativePickupCommitment(true);
        check.checkNewOrder(order(PickupType.NORMAL), cart);
        verifyNoInteractions(availability);
    }

    @Test void approvedWholeCartAndChosenSlotCanProceedWithoutHoldingInventory() {
        enabled();
        when(availability.check(1L, day, 1, cart)).thenReturn(result(true, false, List.of(), null));
        assertThatCode(() -> check.checkNewOrder(order(PickupType.NORMAL), cart)).doesNotThrowAnyException();
        verify(availability).check(1L, day, 1, cart);
        verifyNoMoreInteractions(availability);
    }

    @Test void fullSlotOrShortItemRejectsBeforeAtomicReservation() {
        enabled();
        when(availability.check(1L, day, 1, cart)).thenReturn(result(false, false, List.of(), "Fully booked"));
        assertThatThrownBy(() -> check.checkNewOrder(order(PickupType.NORMAL), cart))
                .isInstanceOf(InventoryConflictException.class).hasMessageContaining("Fully booked");

        var shortage = new CartAvailabilityService.ItemAvailability(11L, "Sweet", "GRAM",
                BigDecimal.valueOf(250), BigDecimal.valueOf(100), false,
                "QUANTITY_TOO_LARGE", "Only 100 g remains", null);
        when(availability.check(1L, day, 1, cart)).thenReturn(result(true, true, List.of(shortage), "Only 100 g remains"));
        assertThatThrownBy(() -> check.checkNewOrder(order(PickupType.PRIORITY), cart))
                .isInstanceOf(InventoryConflictException.class).hasMessageContaining("Only 100 g remains");
    }

    @Test void wrongSlotOrWrongPickupModeCannotBorrowOtherCapacity() {
        enabled();
        when(availability.check(1L, day, 1, cart)).thenReturn(result(false, true, List.of(), null));
        assertThatThrownBy(() -> check.checkNewOrder(order(PickupType.NORMAL), cart))
                .isInstanceOf(InventoryConflictException.class);
        assertThatCode(() -> check.checkNewOrder(order(PickupType.PRIORITY), cart)).doesNotThrowAnyException();
        assertThatThrownBy(() -> check.checkNewOrder(order(PickupType.ADMIN_OVERRIDE), cart))
                .isInstanceOf(InventoryConflictException.class);
        var missingSlot = new CartAvailabilityService.DateAvailability(day, true, List.of(), List.of(), null);
        when(availability.check(1L, day, 1, cart)).thenReturn(
                new CartAvailabilityService.Availability("PICKUP", day, day, List.of(missingSlot)));
        assertThatThrownBy(() -> check.checkNewOrder(order(PickupType.NORMAL), cart))
                .isInstanceOf(InventoryConflictException.class).hasMessageContaining("no longer available");
    }

    private void enabled() {
        flags.setSmartAvailability(true);
        flags.setAuthoritativePickupCommitment(true);
    }

    private ValidatedOrderData order(PickupType mode) {
        var branch = new Branch(); branch.setId(1L);
        var slot = new PickupSlot(); slot.setId(2L); slot.setSlotDate(day);
        return new ValidatedOrderData(branch, slot, mode, List.of());
    }

    private CartAvailabilityService.Availability result(boolean normal, boolean priority,
                                                         List<CartAvailabilityService.ItemAvailability> issues, String reason) {
        var slot = new PickupSlotResponse(2L, 1L, day, LocalTime.NOON, LocalTime.of(12, 30),
                1, 0, 1, true, true, 1, 0, 1, BigDecimal.ZERO);
        var offered = new CartAvailabilityService.SlotAvailability(slot, normal, priority, reason,
                reason == null ? null : "SLOT_FULL", issues);
        var date = new CartAvailabilityService.DateAvailability(day, normal || priority, List.of(offered), issues, reason);
        return new CartAvailabilityService.Availability("PICKUP", day, day, List.of(date));
    }
}
