package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentCreationResult;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentAttemptPersistenceService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment storeProviderDetails(
            Long paymentId,
            PaymentCreationResult result
    ) {
        Payment payment = paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment does not exist."
                ));

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "This payment attempt is no longer pending."
            );
        }
        if (result.providerOrderId() == null
                || result.providerOrderId().isBlank()) {
            throw new IllegalStateException(
                    "The payment provider did not return an order reference."
            );
        }

        payment.setProviderOrderId(result.providerOrderId());
        if (result.providerPaymentId() != null
                && !result.providerPaymentId().isBlank()) {
            payment.setProviderPaymentId(result.providerPaymentId());
        }
        return paymentRepository.saveAndFlush(payment);
    }
}
