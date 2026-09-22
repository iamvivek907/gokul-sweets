package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.provider.RefundResult;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final PaymentProviderRegistry providerRegistry;

    public int processPendingRefunds() {
        List<Payment> pending = paymentRepository
                .findTop100ByPaymentStatusOrderByUpdatedAtAsc(
                        PaymentStatus.REFUND_PENDING
                );

        int processed = 0;
        for (Payment payment : pending) {
            try {
                processPendingRefund(payment.getId());
                processed++;
            } catch (RuntimeException exception) {
                log.error(
                        "Refund reconciliation failed and will be retried: paymentId={}, provider={}",
                        payment.getId(),
                        payment.getProvider(),
                        exception
                );
            }
        }
        return processed;
    }

    public void processPendingRefund(Long paymentId) {
        Payment payment = paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment does not exist."
                ));

        if (payment.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
            return;
        }

        PaymentProvider provider = providerRegistry.require(
                payment.getProvider()
        );
        boolean initiation = payment.getRefundRequestedAt() == null;
        RefundResult result = initiation
                ? provider.refund(payment)
                : provider.verifyRefund(payment);

        apply(paymentId, result, initiation);
    }

    private void apply(
            Long paymentId,
            RefundResult result,
            boolean initiation
    ) {
        switch (result.paymentStatus()) {
            case REFUND_PENDING -> {
                if (initiation) {
                    paymentStatusService.markRefundInitiated(
                            paymentId,
                            result.providerRefundId()
                    );
                } else {
                    paymentStatusService.markRefundStillPending(
                            paymentId,
                            result.providerRefundId()
                    );
                }
            }
            case REFUNDED -> paymentStatusService.markRefunded(
                    paymentId,
                    result.providerRefundId()
            );
            case REFUND_FAILED -> paymentStatusService.markRefundFailed(
                    paymentId,
                    result.providerRefundId(),
                    result.failureReason()
            );
            default -> throw new IllegalStateException(
                    "Unexpected refund status: " + result.paymentStatus()
            );
        }
    }
}
