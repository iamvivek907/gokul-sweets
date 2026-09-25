package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentReconciliationPolicy {
    private final EnhancementProperties features;

    public boolean isLate(Payment payment, LocalDateTime now) {
        if (!features.isPaymentReconciliationV2()) return false;
        LocalDateTime paymentDeadline = payment.getExpiresAt();
        LocalDateTime reservationDeadline = payment.getOrder().getReservationExpiresAt();
        return paymentDeadline == null || reservationDeadline == null
                || !paymentDeadline.isAfter(now) || !reservationDeadline.isAfter(now);
    }

    public void validatePaytmStatus(Payment payment, PaytmStatusResponse.Body body) {
        if (!features.isPaymentReconciliationV2()) return;
        if (body.orderId() == null || !body.orderId().equals(payment.getProviderOrderId())) {
            throw new IllegalStateException("Paytm status order ID does not match this payment.");
        }
        if (body.txnAmount() == null) {
            throw new IllegalStateException("Paytm status does not contain a payment amount.");
        }
        try {
            if (new BigDecimal(body.txnAmount()).compareTo(payment.getAmount()) != 0) {
                throw new IllegalStateException("Paytm status amount does not match this payment.");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Paytm status amount is invalid.", exception);
        }
    }
}
