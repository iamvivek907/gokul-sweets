package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryCommitmentService;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import com.gokulsweets.restaurant.rebate.RebateRedemptionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentStatusLateSuccessTest {
    @Test
    void duplicateSuccessAfterRefundIsQueuedDoesNotReopenFulfilment() {
        var repository = mock(PaymentRepository.class);
        var slots = mock(PickupSlotReservationService.class);
        var commitments = mock(OrderInventoryCommitmentService.class);
        var holds = mock(OrderInventoryReservationService.class);
        var service = new PaymentStatusService(repository, mock(OrderRepository.class), slots,
                mock(RebateRedemptionService.class), commitments, holds,
                new PaymentReconciliationPolicy(new EnhancementProperties()));
        var order = new Order();
        order.setOrderStatus(OrderStatus.CANCELLED);
        Payment queued = payment(order, PaymentStatus.REFUND_PENDING);
        when(repository.findById(21L)).thenReturn(Optional.of(queued));
        service.markPaid(21L, "same-provider-transaction");
        verify(repository, never()).transitionStatusFromAny(any(), any(), any());
        verifyNoInteractions(slots, holds, commitments);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void successAfterDeadlineReleasesHoldAndStartsOneRefundWithoutConfirmingOrder() {
        var repository = mock(PaymentRepository.class);
        var orders = mock(OrderRepository.class);
        var slots = mock(PickupSlotReservationService.class);
        var redemptions = mock(RebateRedemptionService.class);
        var commitments = mock(OrderInventoryCommitmentService.class);
        var holds = mock(OrderInventoryReservationService.class);
        var features = new EnhancementProperties();
        features.setPaymentReconciliationV2(true);
        var service = new PaymentStatusService(repository, orders, slots, redemptions,
                commitments, holds, new PaymentReconciliationPolicy(features));

        var order = new Order();
        order.setId(11L);
        order.setOrderNumber("GKS-TEST");
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setPickupType(PickupType.NORMAL);
        order.setReservationExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(1));
        var slot = new PickupSlot();
        slot.setId(42L);
        order.setPickupSlot(slot);
        Payment pending = payment(order, PaymentStatus.PENDING);
        pending.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(1));
        Payment expired = payment(order, PaymentStatus.EXPIRED);
        Payment refund = payment(order, PaymentStatus.REFUND_PENDING);
        when(repository.findById(21L)).thenReturn(Optional.of(pending), Optional.of(expired),
                Optional.of(expired), Optional.of(refund));
        when(repository.transitionStatus(21L, PaymentStatus.PENDING, PaymentStatus.EXPIRED)).thenReturn(1);
        when(repository.transitionStatusFromAny(eq(21L), eq(List.of(PaymentStatus.FAILED,
                PaymentStatus.EXPIRED)), eq(PaymentStatus.REFUND_PENDING))).thenReturn(1);

        service.markPaid(21L, "provider-transaction");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(refund.getProviderPaymentId()).isEqualTo("provider-transaction");
        assertThat(refund.getRefundReferenceId()).isNotBlank();
        verify(slots, times(1)).releaseNormalCapacity(42L);
        verify(holds, times(1)).releasePendingOrderHolds(eq("GKS-TEST"), any());
        verifyNoInteractions(commitments, redemptions);
    }

    private static Payment payment(Order order, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(21L);
        payment.setOrder(order);
        payment.setPaymentStatus(status);
        return payment;
    }
}
