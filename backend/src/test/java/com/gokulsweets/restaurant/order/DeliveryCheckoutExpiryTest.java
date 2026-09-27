package com.gokulsweets.restaurant.order;

import com.gokulsweets.restaurant.delivery.DeliveryRiderHoldService;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.CheckoutReservationExpiryProcessor;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeliveryCheckoutExpiryTest {
    @Test
    void unpaidDeliveryExpiryReleasesRiderWithoutTouchingPickupSlot() {
        var orders = mock(OrderRepository.class);
        var payments = mock(PaymentRepository.class);
        var pickup = mock(PickupSlotReservationService.class);
        var inventory = mock(OrderInventoryReservationService.class);
        var riders = mock(DeliveryRiderHoldService.class);
        var order = new Order();
        order.setId(18L);
        order.setOrderNumber("GKS-DELIVERY");
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setDeliveryHoldKey("server-issued-hold");
        order.setReservationExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(1));
        when(orders.findForUpdate("GKS-DELIVERY")).thenReturn(Optional.of(order));
        when(riders.release("server-issued-hold")).thenReturn(true);
        var processor = new CheckoutReservationExpiryProcessor(orders, payments, pickup, inventory, riders);

        assertThat(processor.expireReservation("GKS-DELIVERY")).isTrue();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(riders).release("server-issued-hold");
        verify(inventory).releasePendingOrderHolds("GKS-DELIVERY", "Checkout reservation expired.");
        verifyNoInteractions(pickup);
    }
}
