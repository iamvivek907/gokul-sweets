package com.gokulsweets.restaurant.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="gokul.jobs.background-enabled",havingValue="true",matchIfMissing=true)
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundScheduler {

    private final PaymentRefundService paymentRefundService;

    @Scheduled(
            fixedDelayString =
                    "${payment.refund-check-interval-ms:60000}"
    )
    public void processPendingRefunds() {

        int processedCount =
                paymentRefundService
                        .processPendingRefunds();

        if (
                processedCount > 0
        ) {

            log.info(
                    "Processed pending payment refunds: count={}",
                    processedCount
            );
        }
    }
}
