package com.gokulsweets.restaurant.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryScheduler {

    private final PaymentExpiryService paymentExpiryService;

    @Scheduled(
            fixedDelayString =
                    "${payment.expiry-check-interval-ms:60000}"
    )
    public void expirePendingPayments() {

        int expiredCount =
                paymentExpiryService
                        .expirePendingPayments();

        if (expiredCount > 0) {

            log.info(
                    "Expired stale pending payments: count={}",
                    expiredCount
            );
        }
    }
}