package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.inventory.service.OrderInventoryCommitmentService;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import com.gokulsweets.restaurant.rebate.RebateRedemptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentStatusService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    private final PaymentRepository paymentRepository;

    private final OrderRepository orderRepository;

    private final PickupSlotReservationService
            pickupSlotReservationService;

    private final RebateRedemptionService
            rebateRedemptionService;

    private final OrderInventoryCommitmentService
            orderInventoryCommitmentService;

    private final OrderInventoryReservationService
            orderInventoryReservationService;

    // =========================================================
    // MARK PAID
    // =========================================================

    @Transactional
    public void markPaid(
            Long paymentId,
            String providerPaymentId
    ) {

        Payment existingPayment =
                getPayment(
                        paymentId
                );

        /*
         * Already PAID:
         *
         * Treat duplicate success callbacks as idempotent.
         *
         * Only repair an order that is still logically inside
         * the paid checkout path. Never resurrect a terminal
         * order whose pickup capacity may already have been
         * released.
         */
        if (
                existingPayment.getPaymentStatus()
                        == PaymentStatus.PAID
        ) {

            Order order =
                    existingPayment.getOrder();

            if (
                    order.getOrderStatus()
                            == OrderStatus.PENDING_PAYMENT
            ) {

                order.setOrderStatus(
                        OrderStatus.CONFIRMED
                );

                orderRepository.save(
                        order
                );

            } else if (
                    order.getOrderStatus()
                            != OrderStatus.CONFIRMED
            ) {

                log.error(
                        "Paid payment is attached to a terminal non-confirmed order; automatic order repair blocked and reconciliation is required: paymentId={}, orderId={}, orderNumber={}, orderStatus={}",
                        paymentId,
                        order.getId(),
                        order.getOrderNumber(),
                        order.getOrderStatus()
                );

                return;
            }

            orderInventoryCommitmentService
                    .confirmPendingOrderHolds(
                            order.getOrderNumber()
                    );

            rebateRedemptionService
                    .recordRedemptionIfApplicable(
                            order
                    );

            log.debug(
                    "Duplicate paid event processed idempotently: paymentId={}, orderId={}",
                    paymentId,
                    order.getId()
            );

            return;
        }

        /*
         * Provider success arrived after the local payment had
         * already become FAILED or EXPIRED.
         *
         * Capacity may already have been released.
         *
         * Never convert this back to PAID automatically.
         * Persist a durable provider transaction reference and
         * reconciliation note instead.
         */
        if (
                existingPayment.getPaymentStatus()
                        == PaymentStatus.FAILED
                        ||
                        existingPayment.getPaymentStatus()
                                == PaymentStatus.EXPIRED
        ) {

            prepareLateSuccessRefund(
                    existingPayment,
                    providerPaymentId
            );

            return;
        }

        /*
         * Atomically win:
         *
         * PENDING -> PAID
         *
         * This competes safely with:
         *
         * PENDING -> FAILED
         * PENDING -> EXPIRED
         */
        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.PENDING,
                        PaymentStatus.PAID
                );

        if (
                updatedRows == 0
        ) {

            Payment latestPayment =
                    getPayment(
                            paymentId
                    );

            if (
                    latestPayment.getPaymentStatus()
                            == PaymentStatus.PAID
            ) {

                Order order =
                        latestPayment.getOrder();

                if (
                        order.getOrderStatus()
                                == OrderStatus.PENDING_PAYMENT
                ) {

                    order.setOrderStatus(
                            OrderStatus.CONFIRMED
                    );

                    orderRepository.save(
                            order
                    );

                } else if (
                        order.getOrderStatus()
                                != OrderStatus.CONFIRMED
                ) {

                    log.error(
                            "Concurrent paid payment is attached to a terminal non-confirmed order; automatic order repair blocked: paymentId={}, orderId={}, orderNumber={}, orderStatus={}",
                            paymentId,
                            order.getId(),
                            order.getOrderNumber(),
                            order.getOrderStatus()
                    );

                    return;
                }

                orderInventoryCommitmentService
                        .confirmPendingOrderHolds(
                                order.getOrderNumber()
                        );

                rebateRedemptionService
                        .recordRedemptionIfApplicable(
                                order
                        );

                return;
            }

            if (
                    latestPayment.getPaymentStatus()
                            == PaymentStatus.FAILED
                            ||
                            latestPayment.getPaymentStatus()
                                    == PaymentStatus.EXPIRED
            ) {

                prepareLateSuccessRefund(
                        latestPayment,
                        providerPaymentId
                );

                return;
            }

            log.warn(
                    "Unable to mark payment paid because payment is no longer pending: paymentId={}, currentStatus={}",
                    paymentId,
                    latestPayment.getPaymentStatus()
            );

            return;
        }

        /*
         * transitionStatus() clears the persistence context.
         * Reload the winning Payment and its order.
         */
        Payment payment =
                getPayment(
                        paymentId
                );

        payment.setProviderPaymentId(
                providerPaymentId
        );

        payment.setPaidAt(
                LocalDateTime.now(
                        BUSINESS_ZONE
                )
        );

        Order order =
                payment.getOrder();

        /*
         * Under the normal invariant the order must still be
         * PENDING_PAYMENT here.
         *
         * Do not confirm a terminal order whose capacity may
         * already have been released.
         */
        if (
                order.getOrderStatus()
                        != OrderStatus.PENDING_PAYMENT
                        &&
                        order.getOrderStatus()
                                != OrderStatus.CONFIRMED
        ) {

            /*
             * Keep the provider transaction reference durable.
             *
             * We deliberately do not touch the order state.
             */
            payment.setFailureReason(
                    truncate(
                            "Provider reported payment success, but the order is already "
                                    + order.getOrderStatus()
                                    + ". Manual reconciliation/refund is required."
                    )
            );

            paymentRepository.save(
                    payment
            );

            log.error(
                    "Payment became PAID while order was already terminal; order confirmation blocked and reconciliation is required: paymentId={}, orderId={}, orderNumber={}, orderStatus={}",
                    paymentId,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getOrderStatus()
            );

            return;
        }

        order.setOrderStatus(
                OrderStatus.CONFIRMED
        );

        paymentRepository.save(
                payment
        );

        orderRepository.save(
                order
        );

        orderInventoryCommitmentService
                .confirmPendingOrderHolds(
                        order.getOrderNumber()
                );

        rebateRedemptionService
                .recordRedemptionIfApplicable(
                        order
                );

        log.info(
                "Payment marked paid, order confirmed and rebate redemption processed: paymentId={}, orderId={}",
                paymentId,
                order.getId()
        );
    }

    // =========================================================
    // LATE PROVIDER SUCCESS -> REFUND
    // =========================================================

    private void prepareLateSuccessRefund(
            Payment payment,
            String providerPaymentId
    ) {

        PaymentStatus currentStatus =
                payment.getPaymentStatus();

        if (
                currentStatus
                        != PaymentStatus.FAILED
                        &&
                        currentStatus
                                != PaymentStatus.EXPIRED
        ) {

            return;
        }

        /*
         * Atomically claim this terminal payment for the
         * automatic refund workflow.
         *
         * Duplicate provider-success callbacks therefore do not
         * create multiple independent refund workflows.
         */
        int updatedRows =
                paymentRepository
                        .transitionStatusFromAny(
                                payment.getId(),
                                java.util.List.of(
                                        PaymentStatus.FAILED,
                                        PaymentStatus.EXPIRED
                                ),
                                PaymentStatus.REFUND_PENDING
                        );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            payment.getId()
                    );

            log.debug(
                    "Late-success refund preparation skipped because payment state already changed: paymentId={}, currentStatus={}",
                    payment.getId(),
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment refundablePayment =
                getPayment(
                        payment.getId()
                );

        if (
                providerPaymentId != null
                        &&
                        !providerPaymentId.isBlank()
        ) {

            refundablePayment.setProviderPaymentId(
                    providerPaymentId
            );
        }

        if (
                refundablePayment
                        .getRefundReferenceId()
                        == null
                        ||
                        refundablePayment
                                .getRefundReferenceId()
                                .isBlank()
        ) {

            /*
             * One full automatic refund is allowed per payment.
             *
             * A deterministic reference keeps retries idempotent
             * even if the network fails after Paytm accepted the
             * first request.
             */
            refundablePayment.setRefundReferenceId(
                    createRefundReferenceId(
                            refundablePayment.getId()
                    )
            );
        }

        refundablePayment.setRefundFailureReason(
                null
        );

        paymentRepository.save(
                refundablePayment
        );

        log.error(
                "Late provider success moved to automatic refund workflow: paymentId={}, orderId={}, orderNumber={}, previousLocalStatus={}, refundReferenceId={}",
                refundablePayment.getId(),
                refundablePayment.getOrder()
                        .getId(),
                refundablePayment.getOrder()
                        .getOrderNumber(),
                currentStatus,
                refundablePayment
                        .getRefundReferenceId()
        );
    }

    // =========================================================
    // MARK REFUND INITIATED
    // =========================================================

    @Transactional
    public void markRefundInitiated(
            Long paymentId,
            String providerRefundId
    ) {

        Payment payment =
                getPayment(
                        paymentId
                );

        if (
                payment.getPaymentStatus()
                        != PaymentStatus.REFUND_PENDING
        ) {

            return;
        }

        LocalDateTime now =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        if (
                payment.getRefundRequestedAt()
                        == null
        ) {

            payment.setRefundRequestedAt(
                    now
            );
        }

        payment.setRefundLastCheckedAt(
                now
        );

        if (
                providerRefundId != null
                        &&
                        !providerRefundId.isBlank()
        ) {

            payment.setProviderRefundId(
                    providerRefundId
            );
        }

        payment.setRefundFailureReason(
                null
        );

        paymentRepository.save(
                payment
        );
    }

    // =========================================================
    // TOUCH REFUND CHECK
    // =========================================================

    @Transactional
    public void markRefundStillPending(
            Long paymentId,
            String providerRefundId
    ) {

        Payment payment =
                getPayment(
                        paymentId
                );

        if (
                payment.getPaymentStatus()
                        != PaymentStatus.REFUND_PENDING
        ) {

            return;
        }

        if (
                providerRefundId != null
                        &&
                        !providerRefundId.isBlank()
        ) {

            payment.setProviderRefundId(
                    providerRefundId
            );
        }

        payment.setRefundLastCheckedAt(
                LocalDateTime.now(
                        BUSINESS_ZONE
                )
        );

        paymentRepository.save(
                payment
        );
    }

    // =========================================================
    // MARK REFUNDED
    // =========================================================

    @Transactional
    public void markRefunded(
            Long paymentId,
            String providerRefundId
    ) {

        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.REFUND_PENDING,
                        PaymentStatus.REFUNDED
                );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            paymentId
                    );

            if (
                    latest.getPaymentStatus()
                            == PaymentStatus.REFUNDED
            ) {

                return;
            }

            log.warn(
                    "Unable to mark refund completed because payment is not REFUND_PENDING: paymentId={}, currentStatus={}",
                    paymentId,
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment payment =
                getPayment(
                        paymentId
                );

        LocalDateTime now =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        if (
                providerRefundId != null
                        &&
                        !providerRefundId.isBlank()
        ) {

            payment.setProviderRefundId(
                    providerRefundId
            );
        }

        if (
                payment.getRefundRequestedAt()
                        == null
        ) {

            payment.setRefundRequestedAt(
                    now
            );
        }

        payment.setRefundLastCheckedAt(
                now
        );

        payment.setRefundedAt(
                now
        );

        payment.setRefundFailureReason(
                null
        );

        paymentRepository.save(
                payment
        );

        log.info(
                "Payment refund completed: paymentId={}, orderId={}, providerRefundId={}",
                paymentId,
                payment.getOrder()
                        .getId(),
                payment.getProviderRefundId()
        );
    }

    // =========================================================
    // MARK REFUND FAILED
    // =========================================================

    @Transactional
    public void markRefundFailed(
            Long paymentId,
            String providerRefundId,
            String failureReason
    ) {

        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.REFUND_PENDING,
                        PaymentStatus.REFUND_FAILED
                );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            paymentId
                    );

            log.debug(
                    "Refund failure ignored because payment is no longer REFUND_PENDING: paymentId={}, currentStatus={}",
                    paymentId,
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment payment =
                getPayment(
                        paymentId
                );

        if (
                providerRefundId != null
                        &&
                        !providerRefundId.isBlank()
        ) {

            payment.setProviderRefundId(
                    providerRefundId
            );
        }

        payment.setRefundLastCheckedAt(
                LocalDateTime.now(
                        BUSINESS_ZONE
                )
        );

        payment.setRefundFailureReason(
                truncate(
                        failureReason
                )
        );

        paymentRepository.save(
                payment
        );

        log.error(
                "Payment refund failed: paymentId={}, orderId={}, refundReferenceId={}, reason={}",
                paymentId,
                payment.getOrder()
                        .getId(),
                payment.getRefundReferenceId(),
                payment.getRefundFailureReason()
        );
    }

    // =========================================================
    // REFUND REFERENCE
    // =========================================================

    private String createRefundReferenceId(
            Long paymentId
    ) {

        return "GKS-RF-"
                + paymentId;
    }

    // =========================================================
    // MARK INITIALIZATION FAILED
    // =========================================================

    @Transactional
    public void markInitializationFailed(
            Long paymentId,
            String failureReason
    ) {

        Payment current =
                getPayment(
                        paymentId
                );

        if (
                current.getPaymentStatus()
                        == PaymentStatus.PAID
        ) {

            log.warn(
                    "Ignoring initialization failure for already paid payment: paymentId={}",
                    paymentId
            );

            return;
        }

        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.PENDING,
                        PaymentStatus.FAILED
                );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            paymentId
                    );

            log.debug(
                    "Payment initialization failure ignored because payment is no longer pending: paymentId={}, currentStatus={}",
                    paymentId,
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment payment =
                getPayment(
                        paymentId
                );

        payment.setFailureReason(
                truncate(
                        failureReason
                )
        );

        Order order =
                payment.getOrder();

        if (
                order.getOrderStatus()
                        == OrderStatus.PENDING_PAYMENT
        ) {

            order.setOrderStatus(
                    OrderStatus.PAYMENT_FAILED
            );

            releasePickupCapacity(
                    order
            );

            orderInventoryReservationService.releasePendingOrderHolds(
                    order.getOrderNumber(),
                    "Payment initialization failed."
            );

            log.info(
                    "Payment initialization failed; order marked PAYMENT_FAILED and pickup capacity released: paymentId={}, orderId={}, orderNumber={}",
                    paymentId,
                    order.getId(),
                    order.getOrderNumber()
            );

        } else {

            log.warn(
                    "Payment initialization failed but order is no longer PENDING_PAYMENT; capacity was not released again: paymentId={}, orderId={}, orderStatus={}",
                    paymentId,
                    order.getId(),
                    order.getOrderStatus()
            );
        }

        paymentRepository.save(
                payment
        );

        orderRepository.save(
                order
        );
    }

    // =========================================================
    // MARK TRANSACTION FAILED
    // =========================================================

    @Transactional
    public void markFailed(
            Long paymentId,
            String failureReason
    ) {

        Payment current =
                getPayment(
                        paymentId
                );

        if (
                current.getPaymentStatus()
                        == PaymentStatus.PAID
        ) {

            log.warn(
                    "Ignoring failure event for already paid payment: paymentId={}",
                    paymentId
            );

            return;
        }

        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.PENDING,
                        PaymentStatus.FAILED
                );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            paymentId
                    );

            log.debug(
                    "Payment failure ignored because payment is no longer pending: paymentId={}, currentStatus={}",
                    paymentId,
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment payment =
                getPayment(
                        paymentId
                );

        payment.setFailureReason(
                truncate(
                        failureReason
                )
        );

        Order order =
                payment.getOrder();

        if (
                order.getOrderStatus()
                        == OrderStatus.PENDING_PAYMENT
        ) {

            order.setOrderStatus(
                    OrderStatus.PAYMENT_FAILED
            );

            releasePickupCapacity(
                    order
            );

            orderInventoryReservationService.releasePendingOrderHolds(
                    order.getOrderNumber(),
                    "Payment failed."
            );

            log.info(
                    "Payment failed and pickup capacity released: paymentId={}, orderId={}",
                    paymentId,
                    order.getId()
            );

        } else {

            log.warn(
                    "Payment failed but order is no longer PENDING_PAYMENT; capacity was not released again: paymentId={}, orderId={}, orderStatus={}",
                    paymentId,
                    order.getId(),
                    order.getOrderStatus()
            );
        }

        paymentRepository.save(
                payment
        );

        orderRepository.save(
                order
        );
    }

    // =========================================================
    // MARK EXPIRED
    // =========================================================

    @Transactional
    public void markExpired(
            Long paymentId
    ) {

        int updatedRows =
                paymentRepository.transitionStatus(
                        paymentId,
                        PaymentStatus.PENDING,
                        PaymentStatus.EXPIRED
                );

        if (
                updatedRows == 0
        ) {

            Payment latest =
                    getPayment(
                            paymentId
                    );

            log.debug(
                    "Payment expiry skipped because payment is no longer pending: paymentId={}, currentStatus={}",
                    paymentId,
                    latest.getPaymentStatus()
            );

            return;
        }

        Payment payment =
                getPayment(
                        paymentId
                );

        Order order =
                payment.getOrder();

        if (
                order.getOrderStatus()
                        == OrderStatus.PENDING_PAYMENT
        ) {

            order.setOrderStatus(
                    OrderStatus.CANCELLED
            );

            releasePickupCapacity(
                    order
            );

            orderInventoryReservationService.releasePendingOrderHolds(
                    order.getOrderNumber(),
                    "Payment expired."
            );

            orderRepository.save(
                    order
            );

            log.info(
                    "Payment expired and pickup capacity released: paymentId={}, orderId={}",
                    paymentId,
                    order.getId()
            );

        } else {

            log.warn(
                    "Payment expired but order is no longer PENDING_PAYMENT; capacity was not released again: paymentId={}, orderId={}, orderStatus={}",
                    paymentId,
                    order.getId(),
                    order.getOrderStatus()
            );
        }
    }

    // =========================================================
    // GET PAYMENT
    // =========================================================

    private Payment getPayment(
            Long paymentId
    ) {

        return paymentRepository
                .findById(
                        paymentId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Payment does not exist."
                                )
                );
    }

    // =========================================================
    // RELEASE PICKUP CAPACITY
    // =========================================================

    private void releasePickupCapacity(
            Order order
    ) {

        Long slotId =
                order.getPickupSlot()
                        .getId();

        if (
                order.getPickupType()
                        == PickupType.NORMAL
        ) {

            pickupSlotReservationService
                    .releaseNormalCapacity(
                            slotId
                    );

        } else if (
                order.getPickupType()
                        == PickupType.PRIORITY
        ) {

            pickupSlotReservationService
                    .releasePriorityCapacity(
                            slotId
                    );
        }
    }

    // =========================================================
    // TRUNCATE FAILURE REASON
    // =========================================================

    private String truncate(
            String value
    ) {

        if (
                value == null
        ) {

            return null;
        }

        return value.length() <= 500
                ? value
                : value.substring(
                0,
                500
        );
    }
}
