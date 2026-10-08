package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Backend payment refund scheduler contract and implementation. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "gokul.jobs.background-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundScheduler {

    private final PaymentRefundService paymentRefundService;

    /** Processes pending refunds. */
    @Scheduled(fixedDelayString = "${payment.refund-check-interval-ms:60000}")
    public void processPendingRefunds() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundScheduler.class, "processPendingRefunds()");
        try {
            int processedCount = paymentRefundService.processPendingRefunds();
            if (processedCount > 0) {
                log.info("Processed pending payment refunds: count={}", processedCount);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRefundScheduler.class,
                    "processPendingRefunds()");
        }
    }
}
