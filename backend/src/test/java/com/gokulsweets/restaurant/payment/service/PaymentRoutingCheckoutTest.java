package com.gokulsweets.restaurant.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.*;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.payment.routing.PaymentRoutingService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class PaymentRoutingCheckoutTest {
    PaymentService payments = mock(PaymentService.class);
    PaymentRepository repository = mock(PaymentRepository.class);
    PaymentStatusService statuses = mock(PaymentStatusService.class);
    PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    PaymentRoutingService routing = mock(PaymentRoutingService.class);
    PaymentAttemptPersistenceService persistence = mock(PaymentAttemptPersistenceService.class);
    PaymentProvider phonepe = mock(PaymentProvider.class), razorpay = mock(PaymentProvider.class);
    PaymentCheckoutService checkout =
            new PaymentCheckoutService(
                    payments,
                    repository,
                    statuses,
                    registry,
                    routing,
                    persistence,
                    new EnhancementProperties(),
                    mock(CheckoutUrlVault.class));

    Payment attempt(PaymentProviderType provider) {
        var p = new Payment();
        p.setId(1L);
        p.setProvider(provider);
        p.setProviderOrderId("provider-order");
        p.setPaymentStatus(PaymentStatus.PENDING);
        p.setAmount(new BigDecimal("100"));
        p.setCurrency("INR");
        var order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORDER");
        p.setOrder(order);
        return p;
    }

    @Test
    void resumesOriginalAttemptRegardlessOfNewActiveOrBrowserProvider() {
        var p = attempt(PaymentProviderType.PHONEPE);
        when(repository.findForOrderAndStatus("ORDER", PaymentStatus.PENDING))
                .thenReturn(List.of(p));
        when(registry.registered(PaymentProviderType.PHONEPE)).thenReturn(phonepe);
        assertThat(checkout.initiatePayment("ORDER", PaymentProviderType.RAZORPAY).provider())
                .isEqualTo(PaymentProviderType.PHONEPE);
        verifyNoInteractions(routing, payments);
    }

    @Test
    void readinessFailureDoesNotCreateAttemptCallGatewayOrFailOrder() {
        when(routing.forNewPayment()).thenThrow(new IllegalStateException("Invalid checkout key"));
        assertThatThrownBy(() -> checkout.initiatePayment("ORDER", PaymentProviderType.PHONEPE))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments, phonepe, razorpay, persistence, statuses);
    }

    @Test
    void staleBrowserPreferenceCannotCreateAnotherAttemptOnOldProvider() {
        var p = attempt(PaymentProviderType.RAZORPAY);
        when(repository.findForOrderAndStatus("ORDER", PaymentStatus.PENDING))
                .thenReturn(List.of());
        when(routing.forNewPayment()).thenReturn(razorpay);
        when(razorpay.providerType()).thenReturn(PaymentProviderType.RAZORPAY);
        when(payments.createPendingPayment("ORDER", PaymentProviderType.RAZORPAY)).thenReturn(p);
        var result = new PaymentCreationResult(null, "provider-order", null, null, "rzp_test_1");
        when(razorpay.createPayment(p.getOrder(), p)).thenReturn(result);
        when(persistence.storeProviderDetails(1L, result)).thenReturn(p);
        assertThat(checkout.initiatePayment("ORDER", PaymentProviderType.PHONEPE).provider())
                .isEqualTo(PaymentProviderType.RAZORPAY);
        verify(payments, never()).createPendingPayment("ORDER", PaymentProviderType.PHONEPE);
    }
}
