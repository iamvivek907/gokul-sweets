package com.gokulsweets.restaurant.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.*;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.payment.service.*;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class CheckoutCancellationTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentStatusService statuses = mock(PaymentStatusService.class);
    private final PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final PaymentCheckoutService checkout =
            new PaymentCheckoutService(
                    mock(PaymentService.class),
                    payments,
                    statuses,
                    registry,
                    mock(PaymentAttemptPersistenceService.class),
                    new EnhancementProperties(),
                    mock(CheckoutUrlVault.class));

    private Payment pending() {
        var payment = new Payment();
        payment.setId(1L);
        payment.setProvider(PaymentProviderType.PHONEPE);
        payment.setProviderOrderId("attempt-1");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        var order = new Order();
        order.setOrderNumber("GKS-TEST");
        payment.setOrder(order);
        when(payments.findByIdWithOrder(1L)).thenReturn(Optional.of(payment));
        when(registry.require(PaymentProviderType.PHONEPE)).thenReturn(provider);
        return payment;
    }

    @Test
    void outageLeavesStatusAndReservationsUntouched() {
        var p = pending();
        when(provider.verifyPayment(p)).thenThrow(new IllegalStateException("Unavailable"));
        assertThatThrownBy(() -> checkout.cancelCheckout(1L))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(statuses);
    }

    @Test
    void verifiedPendingUsesExistingAtomicExpiryRelease() {
        var p = pending();
        when(provider.verifyPayment(p))
                .thenReturn(new PaymentVerificationResult(PaymentStatus.PENDING, null, null));
        checkout.cancelCheckout(1L);
        verify(statuses).markExpired(1L);
        verify(statuses, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void providerPaidPreservesConfirmedOrder() {
        var p = pending();
        when(provider.verifyPayment(p))
                .thenReturn(new PaymentVerificationResult(PaymentStatus.PAID, "capture", null));
        doAnswer(
                        inv -> {
                            p.setPaymentStatus(PaymentStatus.PAID);
                            return null;
                        })
                .when(statuses)
                .markPaid(1L, "capture");
        assertThat(checkout.cancelCheckout(1L).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(statuses, never()).markExpired(anyLong());
    }

    @Test
    void lateSuccessUsesExistingRefundLifecycle() {
        var p = pending();
        p.setPaymentStatus(PaymentStatus.EXPIRED);
        when(provider.verifyPayment(p))
                .thenReturn(new PaymentVerificationResult(PaymentStatus.PAID, "late", null));
        checkout.refreshPayment(1L);
        verify(statuses).markPaid(1L, "late");
    }
}
