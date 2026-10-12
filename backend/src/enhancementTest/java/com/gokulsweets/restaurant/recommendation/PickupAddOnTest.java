package com.gokulsweets.restaurant.recommendation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.service.CustomerInventoryAvailabilityService;
import com.gokulsweets.restaurant.menu.MenuService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.*;

class PickupAddOnTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MenuService menu = mock(MenuService.class);
    private final CustomerInventoryAvailabilityService inventory =
            mock(CustomerInventoryAvailabilityService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderValidationService validation = mock(OrderValidationService.class);
    private final EnhancementProperties flags = new EnhancementProperties();
    private final PickupAddOnService service =
            new PickupAddOnService(
                    jdbc,
                    menu,
                    inventory,
                    orders,
                    validation,
                    flags,
                    Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"), ZoneOffset.UTC),
                    mock(com.gokulsweets.restaurant.order.service.CartAvailabilityService.class));
    private final CustomerInventoryCheckRequest request =
            new CustomerInventoryCheckRequest(
                    LocalDate.of(2026, 10, 1),
                    List.of(new CustomerInventoryCheckRequest.Item(1L, 1, null)));

    private Order owned() {
        var b = new Branch();
        b.setId(1L);
        var slot = new PickupSlot();
        slot.setId(2L);
        slot.setSlotDate(request.serviceDate());
        var order = new Order();
        order.setBranch(b);
        order.setPickupSlot(slot);
        order.setPickupType(PickupType.NORMAL);
        order.setFulfillmentType(FulfillmentType.PICKUP);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        when(orders.findByOrderNumber("GKS-OWN")).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void ownStockIsCheckedThroughReservationUpdateRatherThanSubtractingItTwice() {
        var own = owned();
        assertThat(service.check(1, request, "GKS-OWN").orderable()).isTrue();
        verify(validation)
                .validateExistingReservationUpdate(
                        eq(own), eq(2L), eq(PickupType.NORMAL), anyList());
        verifyNoInteractions(inventory);
    }

    @Test
    void failedAvailabilityDoesNotAddOrMutateAnything() {
        var own = owned();
        when(validation.validateExistingReservationUpdate(
                        eq(own), eq(2L), eq(PickupType.NORMAL), anyList()))
                .thenThrow(new IllegalStateException("Unavailable"));
        assertThat(service.check(1, request, "GKS-OWN").orderable()).isFalse();
        verify(orders, never()).save(any());
    }

    @Test
    void foreignBranchAndNonPendingOrdersAreRejected() {
        owned();
        assertThatThrownBy(() -> service.check(2, request, "GKS-OWN"))
                .isInstanceOf(IllegalStateException.class);
        var own = owned();
        own.setOrderStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.check(1, request, "GKS-OWN"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledAnalyticsNeverBlocksCheckoutOrCallsReporting() {
        assertThat(service.recommend(1, request, null)).isEmpty();
        verifyNoInteractions(jdbc, menu, inventory);
    }

    @Test
    void futureWindowRejectsPastDateInIndia() {
        flags.setPickupAddOns(true);
        assertThatThrownBy(
                        () ->
                                service.recommend(
                                        1,
                                        new CustomerInventoryCheckRequest(
                                                LocalDate.of(2026, 9, 30), request.items()),
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
