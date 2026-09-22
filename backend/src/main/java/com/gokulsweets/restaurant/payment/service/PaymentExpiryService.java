package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.provider.PaymentVerificationResult;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final PaymentProviderRegistry providerRegistry;

    public int expirePendingPayments() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<Payment> candidates = paymentRepository
                .findTop100ByPaymentStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        PaymentStatus.PENDING,
                        now
                );

        int processed = 0;
        for (Payment payment : candidates) {
            try {
                PaymentProvider provider = providerRegistry.require(
                        payment.getProvider()
                );
                PaymentVerificationResult verification =
                        provider.verifyPayment(payment);

                switch (verification.paymentStatus()) {
                    case PAID -> paymentStatusService.markPaid(
                            payment.getId(),
                            verification.providerPaymentId()
                    );
                    case FAILED -> paymentStatusService.markFailed(
                            payment.getId(),
                            verification.failureReason()
                    );
                    case PENDING -> paymentStatusService.markExpired(
                            payment.getId()
                    );
                    default -> {
                        log.error(
                                "Unexpected provider status during expiry; reservation retained: paymentId={}, provider={}, status={}",
                                payment.getId(),
                                payment.getProvider(),
                                verification.paymentStatus()
                        );
                        continue;
                    }
                }
                processed++;
            } catch (RuntimeException exception) {
                log.error(
                        "Payment expiry reconciliation failed; reservation retained for retry: paymentId={}, provider={}",
                        payment.getId(),
                        payment.getProvider(),
                        exception
                );
            }
        }
        return processed;
    }
}
