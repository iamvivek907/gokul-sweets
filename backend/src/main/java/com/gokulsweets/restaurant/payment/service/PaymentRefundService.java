package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.provider.RefundResult;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Coordinates payment refund operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final PaymentRepository paymentRepository;

    private final PaymentStatusService paymentStatusService;

    private final PaymentProviderRegistry providerRegistry;

    /**
     * Processes pending refunds.
     *
     * @return the process pending refunds result
     */
    public int processPendingRefunds() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "processPendingRefunds()");
        try {
            int processed = 0;
            // Keep individual batches small on the shared scheduler and select only due IDs.
            for (Long id : paymentRepository.findDueRefundIds(now(), PageRequest.of(0, 10))) {
                if (reconcile(id)) processed++;
            }
            return processed;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRefundService.class,
                    "processPendingRefunds()");
        }
    }

    /**
     * Processes pending refund.
     *
     * @param paymentId the payment id
     */
    public void processPendingRefund(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "processPendingRefund(Long)");
        try {
            reconcile(paymentId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRefundService.class,
                    "processPendingRefund(Long)");
        }
    }

    /**
     * Reconciles payment refund data and returns the {@code boolean} result.
     *
     * <p>Delegates to {@code paymentRepository.claimRefundCheck(...)}, {@code
     * paymentRepository.findByIdWithOrder(...)}, {@code
     * paymentRepository.recordRefundSubmissionAttempt(...)}, {@code provider.refund(...)}.
     *
     * @param paymentId the payment id supplied to this method
     * @return the {@code boolean} result
     */
    private boolean reconcile(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "reconcile(Long)");
        try {
            LocalDateTime started = now();
            LocalDateTime leaseUntil = started.plusMinutes(20);
            if (paymentRepository.claimRefundCheck(paymentId, started, leaseUntil) == 0)
                return false;
            Payment payment = null;
            try {
                payment =
                        paymentRepository
                                .findByIdWithOrder(paymentId)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Payment does not exist."));
                if (payment.getPaymentStatus() != PaymentStatus.REFUND_PENDING) return false;
                PaymentProvider provider = providerRegistry.require(payment.getProvider());
                boolean initiation = payment.getRefundRequestedAt() == null;
                if (initiation) {
                    // Commit the marker before HTTP. Preserve the pre-marker snapshot so only a
                    // genuinely new attempt may POST directly; restart/lost-ack recovery checks
                    // first.
                    int recorded =
                            paymentRepository.recordRefundSubmissionAttempt(paymentId, started);
                    if (payment.getRefundSubmissionAttemptedAt() == null && recorded == 0)
                        return false;
                }
                RefundResult result =
                        initiation ? provider.refund(payment) : provider.verifyRefund(payment);
                apply(paymentId, result, initiation);
                paymentRepository.finishRefundCheck(
                        paymentId, leaseUntil, now().plusMinutes(2), 0, false, null, now());
                return true;
            } catch (RuntimeException exception) {
                int failures =
                        payment == null ? 1 : Math.min(30, payment.getRefundCheckFailures() + 1);
                boolean review = failures >= 8;
                long minutes = Math.min(60L, 1L << Math.min(6, failures - 1));
                String code =
                        exception instanceof PaymentGatewayException gateway
                                ? safeCode(gateway.getCode())
                                : "REFUND_RECONCILIATION_ERROR";
                LocalDateTime next = now().plusMinutes(minutes);
                String reason =
                        (review ? "Staff review required. " : "Refund status uncertain. ")
                                + "Reconciliation will retry; code="
                                + code
                                + ". Verify provider status before any manual refund.";
                paymentRepository.finishRefundCheck(
                        paymentId, leaseUntil, next, failures, review, reason, now());
                // No payload, credentials, customer details or provider-controlled exception
                // messages.
                log.warn(
                        "Refund reconciliation deferred: paymentId={}, code={}, failures={},"
                                + " reviewRequired={}, nextCheck={}",
                        paymentId,
                        code,
                        failures,
                        review,
                        next);
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRefundService.class, "reconcile(Long)");
        }
    }

    /**
     * Safes code.
     *
     * @param code the code
     * @return the safe code result
     */
    private static String safeCode(String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "safeCode(String)");
        try {
            return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "PROVIDER_ERROR";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRefundService.class, "safeCode(String)");
        }
    }

    /**
     * Returns now information for payment refund.
     *
     * @return the value of {@code LocalDateTime.now(BUSINESS_ZONE)}
     */
    private static LocalDateTime now() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "now()");
        try {
            return LocalDateTime.now(BUSINESS_ZONE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentRefundService.class, "now()");
        }
    }

    /**
     * Applies payment refund data.
     *
     * <p>Delegates to {@code paymentStatusService.markRefundInitiated(...)}, {@code
     * paymentStatusService.markRefundStillPending(...)}, {@code
     * paymentStatusService.markRefunded(...)}, {@code paymentStatusService.markRefundFailed(...)}.
     *
     * @param paymentId the payment id supplied to this method
     * @param result the result supplied to this method
     * @param initiation the initiation supplied to this method
     */
    private void apply(Long paymentId, RefundResult result, boolean initiation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRefundService.class, "apply(Long,RefundResult,boolean)");
        try {
            switch (result.paymentStatus()) {
                case REFUND_PENDING -> {
                    if (initiation)
                        paymentStatusService.markRefundInitiated(
                                paymentId, result.providerRefundId());
                    else
                        paymentStatusService.markRefundStillPending(
                                paymentId, result.providerRefundId());
                }
                case REFUNDED ->
                        paymentStatusService.markRefunded(paymentId, result.providerRefundId());
                case REFUND_FAILED ->
                        paymentStatusService.markRefundFailed(
                                paymentId, result.providerRefundId(), result.failureReason());
                default ->
                        throw new IllegalStateException(
                                "Unexpected refund status: " + result.paymentStatus());
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRefundService.class,
                    "apply(Long,RefundResult,boolean)");
        }
    }
}
