package com.gokulsweets.restaurant.payment.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCheckoutService {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentAttemptPersistenceService persistenceService;


    /*
     * =========================================================
     * CREATE / RESUME PAYMENT
     * =========================================================
     */

    public PaymentResponse initiatePayment(
            String orderNumber,
            PaymentProviderType requestedProvider
    ) {

        PaymentProvider provider =
                providerRegistry.require(
                        requestedProvider
                );


        List<Payment> existingPending =
                paymentRepository.findForOrderAndStatus(
                        orderNumber,
                        PaymentStatus.PENDING
                );


        if (
                !existingPending.isEmpty()
        ) {

            Payment existing =
                    existingPending.getFirst();


            if (
                    existing.getProvider()
                            != provider.providerType()
            ) {

                throw new IllegalStateException(
                        "A payment attempt is already in progress. Check its status before selecting another provider."
                );
            }


            if (
                    existing.getProviderOrderId() == null
            ) {

                throw new IllegalStateException(
                        "The previous payment is still being initialized. Please try again shortly."
                );
            }


            log.info(
                    "Returning resumable payment attempt: paymentId={}, orderNumber={}, provider={}",
                    existing.getId(),
                    orderNumber,
                    existing.getProvider()
            );


            return toResponse(
                    existing,
                    null,
                    provider
            );
        }


        Payment payment =
                paymentService.createPendingPayment(
                        orderNumber,
                        provider.providerType()
                );


        try {

            PaymentCreationResult result =
                    provider.createPayment(
                            payment.getOrder(),
                            payment
                    );


            payment =
                    persistenceService.storeProviderDetails(
                            payment.getId(),
                            result
                    );


            log.info(
                    "Payment checkout initialized: paymentId={}, orderNumber={}, provider={}, providerOrderId={}",
                    payment.getId(),
                    orderNumber,
                    payment.getProvider(),
                    payment.getProviderOrderId()
            );


            return toResponse(
                    payment,
                    result,
                    provider
            );

        } catch (
                RuntimeException exception
        ) {

            try {

                paymentStatusService.markInitializationFailed(
                        payment.getId(),
                        "Payment gateway initialization failed."
                );

            } catch (
                    RuntimeException stateException
            ) {

                log.error(
                        "Unable to close failed payment initialization: paymentId={}",
                        payment.getId(),
                        stateException
                );
            }


            log.error(
                    "Payment initialization failed: paymentId={}, orderNumber={}, provider={}, errorType={}",
                    payment.getId(),
                    orderNumber,
                    provider.providerType(),
                    exception.getClass().getSimpleName()
            );


            throw exception;
        }
    }


    /*
     * =========================================================
     * RECOVER LATEST PAYMENT FOR ORDER
     * =========================================================
     *
     * This is used when the customer returns from PhonePe,
     * Paytm, or another external checkout and the browser no
     * longer has the paymentId in localStorage.
     *
     * Returning null means:
     *
     *     No payment has ever been created for this order.
     *
     * The frontend can then safely continue into the normal
     * payment creation flow.
     */
    public PaymentResponse getLatestPaymentForOrder(
            String orderNumber
    ) {

        Payment payment =
                paymentRepository
                        .findFirstByOrderOrderNumberOrderByCreatedAtDesc(
                                orderNumber
                        )
                        .orElse(null);


        if (
                payment == null
        ) {

            log.debug(
                    "No payment found for order: orderNumber={}",
                    orderNumber
            );


            return null;
        }


        PaymentProvider provider =
                providerRegistry.require(
                        payment.getProvider()
                );


        log.info(
                "Recovered payment for order: paymentId={}, orderNumber={}, provider={}, status={}",
                payment.getId(),
                orderNumber,
                payment.getProvider(),
                payment.getPaymentStatus()
        );


        return toResponse(
                payment,
                null,
                provider
        );
    }


    /*
     * =========================================================
     * REFRESH PAYMENT
     * =========================================================
     */

    public PaymentResponse refreshPayment(
            Long paymentId
    ) {

        Payment payment =
                requirePayment(
                        paymentId
                );


        PaymentProvider provider =
                providerRegistry.require(
                        payment.getProvider()
                );


        /*
         * These statuses are already final from the payment
         * lifecycle perspective and do not need another provider
         * status request.
         */
        if (
                payment.getPaymentStatus()
                        == PaymentStatus.PAID

                        ||

                        payment.getPaymentStatus()
                                == PaymentStatus.REFUNDED

                        ||

                        payment.getPaymentStatus()
                                == PaymentStatus.REFUND_FAILED

                        ||

                        payment.getPaymentStatus()
                                == PaymentStatus.REFUND_PENDING
        ) {

            return toResponse(
                    payment,
                    null,
                    provider
            );
        }


        /*
         * No provider order means the provider checkout was never
         * successfully initialized.
         */
        if (
                payment.getProviderOrderId() == null
        ) {

            return toResponse(
                    payment,
                    null,
                    provider
            );
        }


        PaymentVerificationResult verification =
                provider.verifyPayment(
                        payment
                );


        applyVerification(
                payment,
                verification
        );


        return toResponse(
                requirePayment(
                        paymentId
                ),
                null,
                provider
        );
    }


    /*
     * =========================================================
     * RESPONSE FOR PAYMENT
     * =========================================================
     */

    public PaymentResponse responseFor(
            Long paymentId
    ) {

        Payment payment =
                requirePayment(
                        paymentId
                );


        return toResponse(
                payment,
                null,
                providerRegistry.require(
                        payment.getProvider()
                )
        );
    }


    /*
     * =========================================================
     * APPLY PROVIDER VERIFICATION
     * =========================================================
     */

    private void applyVerification(
            Payment payment,
            PaymentVerificationResult verification
    ) {

        /*
         * A late provider success must still be handled if the
         * order/payment had already become FAILED or EXPIRED.
         *
         * PaymentStatusService owns the lifecycle transition and
         * refund reconciliation logic.
         */
        if (
                payment.getPaymentStatus()
                        == PaymentStatus.FAILED

                        ||

                        payment.getPaymentStatus()
                                == PaymentStatus.EXPIRED
        ) {

            if (
                    verification.paymentStatus()
                            == PaymentStatus.PAID
            ) {

                paymentStatusService.markPaid(
                        payment.getId(),
                        verification.providerPaymentId()
                );
            }


            return;
        }


        /*
         * Other terminal/refund states should not be changed by
         * a normal provider status refresh.
         */
        if (
                payment.getPaymentStatus()
                        != PaymentStatus.PENDING
        ) {

            return;
        }


        switch (
                verification.paymentStatus()
        ) {

            case PAID -> paymentStatusService.markPaid(
                    payment.getId(),
                    verification.providerPaymentId()
            );


            case FAILED -> paymentStatusService.markFailed(
                    payment.getId(),
                    verification.failureReason()
            );


            case PENDING -> log.debug(
                    "Provider payment remains pending: paymentId={}, provider={}",
                    payment.getId(),
                    payment.getProvider()
            );


            default -> throw new IllegalStateException(
                    "Unexpected provider payment status: "
                            + verification.paymentStatus()
            );
        }
    }


    /*
     * =========================================================
     * REQUIRE PAYMENT
     * =========================================================
     */

    private Payment requirePayment(
            Long paymentId
    ) {

        return paymentRepository
                .findByIdWithOrder(
                        paymentId
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Payment does not exist."
                        )
                );
    }


    /*
     * =========================================================
     * MAP PAYMENT -> RESPONSE
     * =========================================================
     */

    private PaymentResponse toResponse(
            Payment payment,
            PaymentCreationResult result,
            PaymentProvider provider
    ) {

        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getOrderNumber(),
                payment.getProvider(),
                payment.getPaymentStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProviderPaymentId(),
                payment.getProviderOrderId(),
                result == null
                        ? null
                        : result.paymentSessionId(),
                result == null
                        ? null
                        : result.paymentUrl(),
                result != null
                        && result.checkoutKeyId() != null
                        ? result.checkoutKeyId()
                        : provider.checkoutKeyId(),
                payment.getExpiresAt()
        );
    }

    public PaymentResponse findLatestPaymentForOrder(String orderNumber) {
        return paymentRepository
                .findLatestForOrder(orderNumber)
                .stream()
                .findFirst()
                .map(payment -> {
                    PaymentProvider provider = providerRegistry.require(
                            payment.getProvider()
                    );

                    return toResponse(
                            payment,
                            null,
                            provider
                    );
                })
                .orElse(null);
    }
}