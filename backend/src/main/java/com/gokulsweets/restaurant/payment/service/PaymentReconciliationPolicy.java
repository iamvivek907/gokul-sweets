package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmStatusResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Backend payment reconciliation policy contract and implementation. */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationPolicy {

    private final EnhancementProperties features;

    /**
     * Reports whether late.
     *
     * @param payment the payment
     * @param now the now
     * @return the is late result
     */
    public boolean isLate(Payment payment, LocalDateTime now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentReconciliationPolicy.class, "isLate(Payment,LocalDateTime)");
        try {
            if (!features.isPaymentReconciliationV2()) return false;
            LocalDateTime paymentDeadline = payment.getExpiresAt();
            LocalDateTime reservationDeadline = payment.getOrder().getReservationExpiresAt();
            return paymentDeadline == null
                    || reservationDeadline == null
                    || !paymentDeadline.isAfter(now)
                    || !reservationDeadline.isAfter(now);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentReconciliationPolicy.class,
                    "isLate(Payment,LocalDateTime)");
        }
    }

    /**
     * Validates paytm status.
     *
     * @param payment the payment
     * @param body the body
     */
    public void validatePaytmStatus(Payment payment, PaytmStatusResponse.Body body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentReconciliationPolicy.class,
                        "validatePaytmStatus(Payment,PaytmStatusResponse.Body)");
        try {
            if (!features.isPaymentReconciliationV2()) return;
            if (body.orderId() == null || !body.orderId().equals(payment.getProviderOrderId())) {
                throw new IllegalStateException(
                        "Paytm status order ID does not match this payment.");
            }
            if (body.txnAmount() == null) {
                throw new IllegalStateException("Paytm status does not contain a payment amount.");
            }
            try {
                if (new BigDecimal(body.txnAmount()).compareTo(payment.getAmount()) != 0) {
                    throw new IllegalStateException(
                            "Paytm status amount does not match this payment.");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Paytm status amount is invalid.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentReconciliationPolicy.class,
                    "validatePaytmStatus(Payment,PaytmStatusResponse.Body)");
        }
    }
}
