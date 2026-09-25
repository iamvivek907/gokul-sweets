package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmStatusResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentReconciliationPolicyTest {
    private final EnhancementProperties features = new EnhancementProperties();
    private final PaymentReconciliationPolicy policy = new PaymentReconciliationPolicy(features);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 26, 0, 30);

    @Test
    void respectsBothDeadlinesAtTheExactIstBoundary() {
        Payment payment = payment(now.plusSeconds(1), now.plusMinutes(5));
        assertThat(policy.isLate(payment, now)).isFalse();
        features.setPaymentReconciliationV2(true);
        assertThat(policy.isLate(payment, now)).isFalse();
        payment.setExpiresAt(now);
        assertThat(policy.isLate(payment, now)).isTrue();
        payment.setExpiresAt(now.plusMinutes(5));
        payment.getOrder().setReservationExpiresAt(now);
        assertThat(policy.isLate(payment, now)).isTrue();
        payment.getOrder().setReservationExpiresAt(null);
        assertThat(policy.isLate(payment, now)).isTrue();
    }

    @Test
    void verifiesSignedPaytmStatusOrderAndAmountBeforeAcceptingProviderSuccess() {
        Payment payment = payment(now.plusMinutes(5), now.plusMinutes(5));
        payment.setProviderOrderId("GKS-PAY-41");
        payment.setAmount(new BigDecimal("105.00"));
        features.setPaymentReconciliationV2(true);
        policy.validatePaytmStatus(payment, status("GKS-PAY-41", "105.0"));
        assertThatThrownBy(() -> policy.validatePaytmStatus(payment, status("wrong-order", "105.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("order ID");
        assertThatThrownBy(() -> policy.validatePaytmStatus(payment, status("GKS-PAY-41", "106.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("amount");
        assertThatThrownBy(() -> policy.validatePaytmStatus(payment, status("GKS-PAY-41", "bad")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid");
        assertThatThrownBy(() -> policy.validatePaytmStatus(payment, status("GKS-PAY-41", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("amount");
        features.setPaymentReconciliationV2(false);
        policy.validatePaytmStatus(payment, status("wrong-order", "106.00"));
    }

    private static Payment payment(LocalDateTime expiresAt, LocalDateTime reservationExpiresAt) {
        Order order = new Order();
        order.setReservationExpiresAt(reservationExpiresAt);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setExpiresAt(expiresAt);
        return payment;
    }

    private static PaytmStatusResponse.Body status(String orderId, String amount) {
        return new PaytmStatusResponse.Body(null, null, null, orderId, amount, null, null, null);
    }
}
