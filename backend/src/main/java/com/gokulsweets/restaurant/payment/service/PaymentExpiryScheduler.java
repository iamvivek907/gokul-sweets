package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Backend payment expiry scheduler contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryScheduler {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final PaymentExpiryService paymentExpiryService;

    /** Expires pending payments. */
    @Scheduled(fixedDelayString = "${payment.expiry-check-interval-ms:60000}")
    public void expirePendingPayments() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentExpiryScheduler.class, "expirePendingPayments()");
        try {
            if (dedicatedImportWorker) return;
            int expiredCount = paymentExpiryService.expirePendingPayments();
            if (expiredCount > 0) {
                log.info("Expired stale pending payments: count={}", expiredCount);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentExpiryScheduler.class,
                    "expirePendingPayments()");
        }
    }
}
