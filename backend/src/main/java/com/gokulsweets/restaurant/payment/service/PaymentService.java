package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.Method…1246 tokens truncated…   if (pendingPaymentExists) {
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
            if (provider == PaymentProviderType.RAZORPAY
                    && paymentAmount.compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException(
                        "Razorpay order amount must be at least 100 paise.");
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
