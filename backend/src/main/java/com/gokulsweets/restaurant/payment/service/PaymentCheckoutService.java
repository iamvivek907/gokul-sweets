package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.dto.PaymentResponse;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentCreationResult;
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

/** Coordinates payment checkout operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCheckoutService {

    private final PaymentService paymentService;

    private final PaymentRepository paymentRepository;

    private final PaymentStatusService paymentStatusService;

    private final PaymentProviderRegistry providerRegistry;

    private final PaymentAttemptPersistenceService persistenceService;

    private final EnhancementProperties features;

    private final CheckoutUrlVault checkoutUrlVault;

    /*
     * =========================================================
     * CREATE / RESUME PAYMENT
     * =========================================================
     */
    /**
     * Initiates payment.
     *
     * @param orderNumber the order number
     * @param requestedProvider the requested provider
     * @return the initiate payment result
     */
    public PaymentResponse initiatePayment(
            String orderNumber, PaymentProviderType requestedProvider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentCheckoutService.class,
                        "initiatePayment(String,PaymentProviderType)");
        try {
            PaymentProvider provider = providerRegistry.require(requestedProvider);
            List<Payment> existingPending =
                    paymentRepository.findForOrderAndStatus(orderNumber, PaymentStatus.PENDING);
            if (!existingPending.isEmpty()) {
                Payment existing = existingPending.getFirst();
                if (features.isAcceptedCheckoutQuote()
                        && (existing.getOrder().getReservationExpiresAt() == null
                                || !existing.getOrder()
                                        .getReservationExpiresAt()
                                        .isAfter(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                                || existing.getExpiresAt() == null
                                || !existing.getExpiresAt()
                                        .isAfter(LocalDateTime.now(ZoneId.of("Asia/Kolkata"))))) {
                    throw new IllegalStateException(
                            "This payment window expired. Check your order status before starting a"
                                    + " new checkout.");
                }
                if (existing.getProvider() != provider.providerType()) {
                    throw new IllegalStateException(
                            "A payment attempt is already in progress. Check its status before"
                                    + " selecting another provider.");
                }
                if (existing.getProviderOrderId() == null) {
                    throw new IllegalStateException(
                            "The previous payment is still being initialized. Please try again"
                                    + " shortly.");
                }
                log.info(
                        "Returning resumable payment attempt: paymentId={}, orderNumber={},"
                                + " provider={}",
                        existing.getId(),
                        orderNumber,
                        existing.getProvider());
                return toResponse(existing, null, provider);
            }
            Payment payment =
                    paymentService.createPendingPayment(orderNumber, provider.providerType());
            try {
                PaymentCreationResult result = provider.createPayment(payment.getOrder(), payment);
                payment = persistenceService.storeProviderDetails(payment.getId(), result);
                log.info(
                        "Payment checkout initialized: paymentId={}, orderNumber={}, provider={},"
                                + " providerOrderId={}",
                        payment.getId(),
                        orderNumber,
                        payment.getProvider(),
                        payment.getProviderOrderId());
                return toResponse(payment, result, provider);
            } catch (RuntimeException exception) {
                try {
                    paymentStatusService.markInitializationFailed(
                            payment.getId(), "Payment gateway initialization failed.");
                } catch (RuntimeException stateException) {
                    log.error(
                            "Unable to close failed payment initialization: paymentId={}",
                            payment.getId(),
                            stateException);
                }
                log.error(
                        "Payment initialization failed: paymentId={}, orderNumber={}, provider={},"
                                + " errorType={}",
                        payment.getId(),
                        orderNumber,
                        provider.providerType(),
                        exception.getClass().getSimpleName());
                throw exception;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "initiatePayment(String,PaymentProviderType)");
        }
    }

    /**
     * Returns latest payment for order.
     *
     * @param orderNumber the order number
     * @return the get latest payment for order result
     */
    public PaymentResponse getLatestPaymentForOrder(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentCheckoutService.class, "getLatestPaymentForOrder(String)");
        try {
            Payment payment =
                    paymentRepository
                            .findFirstByOrderOrderNumberOrderByCreatedAtDesc(orderNumber)
                            .orElse(null);
            if (payment == null) {
                log.debug("No payment found for order: orderNumber={}", orderNumber);
                return null;
            }
            PaymentProvider provider = providerRegistry.require(payment.getProvider());
            log.info(
                    "Recovered payment for order: paymentId={}, orderNumber={}, provider={},"
                            + " status={}",
                    payment.getId(),
                    orderNumber,
                    payment.getProvider(),
                    payment.getPaymentStatus());
            return toResponse(payment, null, provider);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "getLatestPaymentForOrder(String)");
        }
    }

    /*
     * =========================================================
     * REFRESH PAYMENT
     * =========================================================
     */
    /**
     * Refreshes payment.
     *
     * @param paymentId the payment id
     * @return the refresh payment result
     */
    public PaymentResponse refreshPayment(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentCheckoutService.class, "refreshPayment(Long)");
        try {
            Payment payment = requirePayment(paymentId);
            PaymentProvider provider = providerRegistry.require(payment.getProvider());
            /*
             * These statuses are already final from the payment
             * lifecycle perspective and do not need another provider
             * status request.
             */
            if (payment.getPaymentStatus() == PaymentStatus.PAID
                    || payment.getPaymentStatus() == PaymentStatus.REFUNDED
                    || payment.getPaymentStatus() == PaymentStatus.REFUND_FAILED
                    || payment.getPaymentStatus() == PaymentStatus.REFUND_PENDING) {
                return toResponse(payment, null, provider);
            }
            /*
             * No provider order means the provider checkout was never
             * successfully initialized.
             */
            if (payment.getProviderOrderId() == null) {
                return toResponse(payment, null, provider);
            }
            PaymentVerificationResult verification = provider.verifyPayment(payment);
            applyVerification(payment, verification);
            return toResponse(requirePayment(paymentId), null, provider);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "refreshPayment(Long)");
        }
    }

    /*
     * =========================================================
     * RESPONSE FOR PAYMENT
     * =========================================================
     */
    /**
     * Provider verification precedes CAS closure; late capture uses the existing refund lifecycle.
     *
     * @param paymentId the payment id
     * @return the operation result
     */
    public PaymentResponse cancelCheckout(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentCheckoutService.class, "cancelCheckout(Long)");
        try {
            var verified = refreshPayment(paymentId);
            if (verified.paymentStatus() == PaymentStatus.PENDING)
                paymentStatusService.markExpired(paymentId);
            if (verified.paymentStatus() == PaymentStatus.FAILED)
                paymentStatusService.cancelFailedOrder(paymentId);
            return responseFor(paymentId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "cancelCheckout(Long)");
        }
    }

    /**
     * Responses for.
     *
     * @param paymentId the payment id
     * @return the response for result
     */
    public PaymentResponse responseFor(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentCheckoutService.class, "responseFor(Long)");
        try {
            Payment payment = requirePayment(paymentId);
            return toResponse(payment, null, providerRegistry.require(payment.getProvider()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentCheckoutService.class, "responseFor(Long)");
        }
    }

    /*
     * =========================================================
     * APPLY PROVIDER VERIFICATION
     * =========================================================
     */
    /**
     * Apply verification.
     *
     * @param payment the payment
     * @param verification the verification
     */
    private void applyVerification(Payment payment, PaymentVerificationResult verification) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentCheckoutService.class,
                        "applyVerification(Payment,PaymentVerificationResult)");
        try {
            /*
             * A late provider success must still be handled if the
             * order/payment had already become FAILED or EXPIRED.
             *
             * PaymentStatusService owns the lifecycle transition and
             * refund reconciliation logic.
             */
            if (payment.getPaymentStatus() == PaymentStatus.FAILED
                    || payment.getPaymentStatus() == PaymentStatus.EXPIRED) {
                if (verification.paymentStatus() == PaymentStatus.PAID) {
                    paymentStatusService.markPaid(
                            payment.getId(), verification.providerPaymentId());
                }
                return;
            }
            /*
             * Other terminal/refund states should not be changed by
             * a normal provider status refresh.
             */
            if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
                return;
            }
            switch (verification.paymentStatus()) {
                case PAID ->
                        paymentStatusService.markPaid(
                                payment.getId(), verification.providerPaymentId());
                case FAILED ->
                        paymentStatusService.markFailed(
                                payment.getId(), verification.failureReason());
                case PENDING ->
                        log.debug(
                                "Provider payment remains pending: paymentId={}, provider={}",
                                payment.getId(),
                                payment.getProvider());
                default ->
                        throw new IllegalStateException(
                                "Unexpected provider payment status: "
                                        + verification.paymentStatus());
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "applyVerification(Payment,PaymentVerificationResult)");
        }
    }

    /*
     * =========================================================
     * REQUIRE PAYMENT
     * =========================================================
     */
    /**
     * Requires payment.
     *
     * @param paymentId the payment id
     * @return the require payment result
     */
    private Payment requirePayment(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentCheckoutService.class, "requirePayment(Long)");
        try {
            return paymentRepository
                    .findByIdWithOrder(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "requirePayment(Long)");
        }
    }

    /*
     * =========================================================
     * MAP PAYMENT -> RESPONSE
     * =========================================================
     */
    /**
     * Tos response.
     *
     * @param payment the payment
     * @param result the result
     * @param provider the provider
     * @return the to response result
     */
    private PaymentResponse toResponse(
            Payment payment, PaymentCreationResult result, PaymentProvider provider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentCheckoutService.class,
                        "toResponse(Payment,PaymentCreationResult,PaymentProvider)");
        try {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getOrder().getOrderNumber(),
                    payment.getProvider(),
                    payment.getPaymentStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProviderPaymentId(),
                    payment.getProviderOrderId(),
                    result == null ? null : result.paymentSessionId(),
                    result == null
                            ? (payment.getPaymentStatus() == PaymentStatus.PENDING
                                    ? checkoutUrlVault.open(payment.getCheckoutUrl())
                                    : null)
                            : result.paymentUrl(),
                    result != null && result.checkoutKeyId() != null
                            ? result.checkoutKeyId()
                            : provider.checkoutKeyId(),
                    payment.getExpiresAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "toResponse(Payment,PaymentCreationResult,PaymentProvider)");
        }
    }

    /**
     * Finds latest payment for order.
     *
     * @param orderNumber the order number
     * @return the find latest payment for order result
     */
    public PaymentResponse findLatestPaymentForOrder(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentCheckoutService.class, "findLatestPaymentForOrder(String)");
        try {
            return paymentRepository.findLatestForOrder(orderNumber).stream()
                    .findFirst()
                    .map(
                            payment -> {
                                PaymentProvider provider =
                                        providerRegistry.require(payment.getProvider());
                                return toResponse(payment, null, provider);
                            })
                    .orElse(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentCheckoutService.class,
                    "findLatestPaymentForOrder(String)");
        }
    }
}
