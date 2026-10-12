package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.RebateApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Coordinates payment operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Value("${payment.pending-expiry-minutes}")
    private long paymentExpiryMinutes;

    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;

    private final PaymentRepository paymentRepository;

    private final OrderRepository orderRepository;

    private final RebateApplicationService rebateApplicationService;

    // =========================================================
    // CREATE PENDING PAYMENT
    // =========================================================
    /**
     * Creates pending payment.
     *
     * @param orderNumber the order number
     * @param provider the provider
     * @return the create pending payment result
     */
    @Transactional
    public Payment createPendingPayment(String orderNumber, PaymentProviderType provider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentService.class, "createPendingPayment(String,PaymentProviderType)");
        try {
            log.info(
                    "Creating payment attempt: orderNumber={}, provider={}", orderNumber, provider);
            /*
             * Lock the order row.
             *
             * The same pessimistic lock is used by:
             *
             * - rebate apply
             * - rebate remove
             * - checkout reservation expiry
             * - cart update
             *
             * This prevents the order payable amount or
             * lifecycle state from changing while a payment
             * attempt is being created.
             */
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Payment creation failed because order does not"
                                                        + " exist: orderNumber={}",
                                                orderNumber);
                                        return new IllegalArgumentException(
                                                "Order does not exist.");
                                    });
            /*
             * The order must still be awaiting payment.
             */
            validateOrderForPayment(order);
            /*
             * The checkout reservation itself must still
             * be active.
             *
             * The scheduler may run once per minute, so we
             * cannot rely only on the scheduler to prevent
             * payment for an already-expired reservation.
             */
            validateReservationForPayment(order);
            /*
             * Never create another payment if this
             * order has already been successfully paid.
             */
            boolean alreadyPaid =
                    paymentRepository.existsByOrderIdAndPaymentStatus(
                            order.getId(), PaymentStatus.PAID);
            if (alreadyPaid) {
                log.warn(
                        "Payment creation rejected because order is already paid: orderId={},"
                                + " orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                throw new IllegalStateException("This order has already been paid.");
            }
            /*
             * Prevent multiple simultaneous payment
             * attempts for the same order.
             */
            boolean pendingPaymentExists =
                    paymentRepository.existsByOrderIdAndPaymentStatus(
                            order.getId(), PaymentStatus.PENDING);
            if (pendingPaymentExists) {
                log.warn(
                        "Payment creation rejected because a pending payment already exists:"
                                + " orderId={}, orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                throw new IllegalStateException(
                        "A payment attempt is already in progress for this order.");
            }
            /*
             * IMPORTANT:
             *
             * Revalidate an applied rebate immediately
             * before freezing the payment amount.
             *
             * This detects cases such as:
             *
             * - rebate was deactivated
             * - rebate expired
             * - customer is no longer eligible
             * - usage limit was reached
             * - calculated rebate amount changed
             */
            loyalty.verifyPayment(order);
            rebateApplicationService.revalidateAppliedRebateBeforePayment(order);
            /*
             * Only read the final payable amount AFTER
             * the rebate has been revalidated.
             */
            BigDecimal paymentAmount = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn(
                        "Payment creation rejected because payable amount is invalid: orderId={},"
                                + " amount={}",
                        order.getId(),
                        paymentAmount);
                throw new IllegalStateException("Order payable amount must be greater than zero.");
            }
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setProvider(provider);
            /*
             * Freeze the backend-calculated order
             * amount into the payment record.
             *
             * Never accept payment amount from frontend.
             */
            payment.setAmount(paymentAmount);
            payment.setCurrency("INR");
            payment.setPaymentStatus(PaymentStatus.PENDING);
            /*
             * IMPORTANT:
             *
             * Starting payment must NEVER extend the original
             * pickup reservation.
             *
             * The payment attempt may have its own configured
             * maximum lifetime, but its effective expiry cannot
             * exceed order.reservationExpiresAt.
             *
             * Example:
             *
             * reservation has 40 seconds remaining
             * payment.pending-expiry-minutes = 15
             *
             * effective payment expiry = reservation expiry,
             * NOT another 15 minutes.
             */
            payment.setExpiresAt(calculateEffectivePaymentExpiry(order));
            Payment saved = paymentRepository.save(payment);
            log.info(
                    "Payment attempt created: paymentId={}, orderId={}, orderNumber={},"
                        + " provider={}, amount={}, paymentExpiresAt={}, reservationExpiresAt={}",
                    saved.getId(),
                    order.getId(),
                    order.getOrderNumber(),
                    provider,
                    saved.getAmount(),
                    saved.getExpiresAt(),
                    order.getReservationExpiresAt());
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentService.class,
                    "createPendingPayment(String,PaymentProviderType)");
        }
    }

    // =========================================================
    // VALIDATE ORDER
    // =========================================================
    /**
     * Validates order for payment.
     *
     * @param order the order
     */
    private void validateOrderForPayment(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentService.class, "validateOrderForPayment(Order)");
        try {
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                log.warn(
                        "Payment creation rejected because order is not awaiting payment:"
                                + " orderId={}, orderNumber={}, status={}",
                        order.getId(),
                        order.getOrderNumber(),
                        order.getOrderStatus());
                throw new IllegalStateException("This order is not awaiting payment.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentService.class,
                    "validateOrderForPayment(Order)");
        }
    }

    // =========================================================
    // VALIDATE CHECKOUT RESERVATION
    // =========================================================
    /**
     * Validates reservation for payment.
     *
     * @param order the order
     */
    private void validateReservationForPayment(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentService.class, "validateReservationForPayment(Order)");
        try {
            LocalDateTime reservationExpiresAt = order.getReservationExpiresAt();
            if (reservationExpiresAt == null) {
                log.error(
                        "Payment creation rejected because reservation expiry is missing:"
                                + " orderId={}, orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                throw new IllegalStateException("Pickup reservation information is unavailable.");
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            /*
             * At exactly the expiry timestamp,
             * the reservation is already expired.
             */
            if (!reservationExpiresAt.isAfter(now)) {
                log.warn(
                        "Payment creation rejected because checkout reservation expired:"
                                + " orderId={}, orderNumber={}, reservationExpiresAt={}",
                        order.getId(),
                        order.getOrderNumber(),
                        reservationExpiresAt);
                throw new IllegalStateException(
                        "Your pickup reservation has expired. Please choose a pickup slot again.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentService.class,
                    "validateReservationForPayment(Order)");
        }
    }

    // =========================================================
    // EFFECTIVE PAYMENT EXPIRY
    // =========================================================
    /**
     * Calculates effective payment expiry.
     *
     * @param order the order
     * @return the calculate effective payment expiry result
     */
    private LocalDateTime calculateEffectivePaymentExpiry(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentService.class, "calculateEffectivePaymentExpiry(Order)");
        try {
            if (paymentExpiryMinutes <= 0) {
                log.error(
                        "Invalid payment expiry configuration: paymentExpiryMinutes={}",
                        paymentExpiryMinutes);
                throw new IllegalStateException("Payment expiry configuration is invalid.");
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            LocalDateTime configuredPaymentExpiry = now.plusMinutes(paymentExpiryMinutes);
            LocalDateTime reservationExpiresAt = order.getReservationExpiresAt();
            /*
             * validateReservationForPayment() already guarantees
             * this exists and is still in the future.
             */
            if (reservationExpiresAt == null) {
                throw new IllegalStateException("Pickup reservation information is unavailable.");
            }
            if (reservationExpiresAt.isBefore(configuredPaymentExpiry)) {
                return reservationExpiresAt;
            }
            return configuredPaymentExpiry;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentService.class,
                    "calculateEffectivePaymentExpiry(Order)");
        }
    }

    // =========================================================
    // UPDATE PROVIDER DETAILS
    // =========================================================
    /**
     * Updates provider details.
     *
     * @param paymentId the payment id
     * @param providerOrderId the provider order id
     * @return the update provider details result
     */
    @Transactional
    public Payment updateProviderDetails(Long paymentId, String providerOrderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentService.class, "updateProviderDetails(Long,String)");
        try {
            if (providerOrderId == null || providerOrderId.isBlank()) {
                throw new IllegalArgumentException("Provider order ID is required.");
            }
            Payment payment =
                    paymentRepository
                            .findById(paymentId)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Unable to update provider details because payment"
                                                        + " does not exist: paymentId={}",
                                                paymentId);
                                        return new IllegalArgumentException(
                                                "Payment does not exist.");
                                    });
            /*
             * Provider identifiers belong to the payment attempt
             * that was actually created. Do not mutate a terminal
             * payment later through a delayed initialization path.
             */
            if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
                log.warn(
                        "Unable to update provider details because payment is no longer pending:"
                                + " paymentId={}, status={}",
                        paymentId,
                        payment.getPaymentStatus());
                throw new IllegalStateException("This payment attempt is no longer pending.");
            }
            payment.setProviderOrderId(providerOrderId);
            Payment saved = paymentRepository.save(payment);
            log.info(
                    "Payment provider details updated: paymentId={}, provider={},"
                            + " providerOrderId={}",
                    saved.getId(),
                    saved.getProvider(),
                    saved.getProviderOrderId());
            return saved;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentService.class,
                    "updateProviderDetails(Long,String)");
        }
    }
}
