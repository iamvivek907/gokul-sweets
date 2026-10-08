package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.dto.AppliedRebateResponse;
import com.gokulsweets.restaurant.rebate.dto.ApplyRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Coordinates rebate application operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RebateApplicationService {

    private final OrderRepository orderRepository;

    private final RebateRepository rebateRepository;

    private final PaymentRepository paymentRepository;

    private final RebateEligibilityService rebateEligibilityService;

    // =========================================================
    // APPLY
    // =========================================================
    /**
     * Automatic selection is atomic with payment creation and never replaces a stronger selected
     * offer.
     */
    @Transactional
    public AppliedRebateResponse applyBest(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateApplicationService.class, "applyBest(String)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            validateOrderMutable(order);
            BigDecimal currentTotal = order.getTotalAmount();
            if (order.isRebateManualSelection()
                    && order.getRebateCode() != null
                    && order.getRebate() != null)
                return new AppliedRebateResponse(
                        orderNumber,
                        order.getRebateCode(),
                        order.getRebate() == null ? null : order.getRebate().getName(),
                        defaultZero(order.getRebateDiscountAmount()),
                        calculateAmountBeforeRebate(order),
                        currentTotal,
                        order.getPaymentFee(),
                        order.getPaymentFeeTax(),
                        order.getPaymentFeeRate());
            if (order.getRebateCode() == null || order.getRebate() == null)
                order.setRebateManualSelection(false);
            var best =
                    rebateEligibilityService.getAvailableRebates(orderNumber).stream()
                            .filter(
                                    offer ->
                                            offer.rebateAmount().signum() > 0
                                                    && offer.payableAfterRebate()
                                                                    .compareTo(currentTotal)
                                                            < 0)
                            .min(
                                    java.util.Comparator.comparing(
                                            AvailableRebateResponse::payableAfterRebate));
            if (best.isEmpty()) {
                return new AppliedRebateResponse(
                        orderNumber,
                        order.getRebateCode(),
                        order.getRebate() == null ? null : order.getRebate().getName(),
                        defaultZero(order.getRebateDiscountAmount()),
                        calculateAmountBeforeRebate(order),
                        currentTotal,
                        order.getPaymentFee(),
                        order.getPaymentFeeTax(),
                        order.getPaymentFeeRate());
            }
            var applied =
                    applySnapshot(orderNumber, new ApplyRebateRequest(best.get().code()), false);
            if (applied.totalAmount().compareTo(currentTotal) > 0) {
                // Configuration may change between discovery and revalidation; rollback rather than
                // raise the customer's price.
                throw new IllegalStateException(
                        "This offer changed. Review the available offers before payment.");
            }
            return applied;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateApplicationService.class, "applyBest(String)");
        }
    }

    /**
     * Reapply manual or best.
     *
     * @param number the number
     * @param manualCode the manual code
     * @return the reapply manual or best result
     */
    @Transactional
    public AppliedRebateResponse reapplyManualOrBest(String number, String manualCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class, "reapplyManualOrBest(String,String)");
        try {
            Order order = orderRepository.findForUpdate(number).orElseThrow();
            validateOrderMutable(order);
            if (manualCode != null
                    && !manualCode.isBlank()
                    && rebateEligibilityService.findEligibleRebate(order, manualCode).isPresent()) {
                return applySnapshot(number, new ApplyRebateRequest(manualCode), true);
            }
            return applyBest(number);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "reapplyManualOrBest(String,String)");
        }
    }

    /**
     * Apply the operation.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the apply result
     */
    @Transactional
    public AppliedRebateResponse apply(String orderNumber, ApplyRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class, "apply(String,ApplyRebateRequest)");
        try {
            return applySnapshot(orderNumber, request, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "apply(String,ApplyRebateRequest)");
        }
    }

    /**
     * Apply snapshot.
     *
     * @param orderNumber the order number
     * @param request the request
     * @param manual the manual
     * @return the apply snapshot result
     */
    private AppliedRebateResponse applySnapshot(
            String orderNumber, ApplyRebateRequest request, boolean manual) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class,
                        "applySnapshot(String,ApplyRebateRequest,boolean)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            validateOrderMutable(order);
            AvailableRebateResponse available =
                    rebateEligibilityService.getEligibleRebate(order, request.code());
            Rebate rebate =
                    rebateRepository
                            .findById(available.rebateId())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Rebate configuration no longer exists."));
            BigDecimal amountBeforeRebate = calculateAmountBeforeRebate(order);
            BigDecimal rebateAmount =
                    available
                            .rebateAmount()
                            .min(amountBeforeRebate)
                            .setScale(2, RoundingMode.HALF_UP);
            BigDecimal newTotal =
                    amountBeforeRebate
                            .subtract(rebateAmount)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
            /*
             * Snapshot the selected rebate onto the order.
             */
            order.setRebate(rebate);
            order.setRebateManualSelection(manual);
            order.setRebateCode(rebate.getCode());
            order.setRebateDiscountAmount(rebateAmount);
            order.setTotalAmount(newTotal);
            com.gokulsweets.restaurant.order.service.PaymentFeePricing.reprice(order);
            orderRepository.save(order);
            log.info(
                    "Rebate applied to order: orderNumber={}, rebateId={}, code={},"
                            + " rebateAmount={}, newTotal={}",
                    order.getOrderNumber(),
                    rebate.getId(),
                    rebate.getCode(),
                    rebateAmount,
                    newTotal);
            return new AppliedRebateResponse(
                    order.getOrderNumber(),
                    rebate.getCode(),
                    rebate.getName(),
                    rebateAmount,
                    amountBeforeRebate,
                    order.getTotalAmount(),
                    order.getPaymentFee(),
                    order.getPaymentFeeTax(),
                    order.getPaymentFeeRate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "applySnapshot(String,ApplyRebateRequest,boolean)");
        }
    }

    // =========================================================
    // REMOVE
    // =========================================================
    /**
     * Removes the operation.
     *
     * @param orderNumber the order number
     * @return the remove result
     */
    @Transactional
    public AppliedRebateResponse remove(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateApplicationService.class, "remove(String)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            validateOrderMutable(order);
            order.setRebateManualSelection(false);
            BigDecimal amountBeforeRebate = calculateAmountBeforeRebate(order);
            /*
             * Idempotent:
             * removing when no rebate exists is okay.
             */
            if (order.getRebate() == null && order.getRebateCode() == null) {
                order.setRebateDiscountAmount(BigDecimal.ZERO);
                order.setTotalAmount(amountBeforeRebate);
                com.gokulsweets.restaurant.order.service.PaymentFeePricing.reprice(order);
                return new AppliedRebateResponse(
                        order.getOrderNumber(),
                        null,
                        null,
                        BigDecimal.ZERO,
                        amountBeforeRebate,
                        order.getTotalAmount(),
                        order.getPaymentFee(),
                        order.getPaymentFeeTax(),
                        order.getPaymentFeeRate());
            }
            String oldCode = order.getRebateCode();
            order.setRebate(null);
            order.setRebateCode(null);
            order.setRebateDiscountAmount(BigDecimal.ZERO);
            order.setTotalAmount(amountBeforeRebate);
            com.gokulsweets.restaurant.order.service.PaymentFeePricing.reprice(order);
            orderRepository.save(order);
            log.info(
                    "Rebate removed from order: orderNumber={}, rebateCode={}, restoredTotal={}",
                    order.getOrderNumber(),
                    oldCode,
                    amountBeforeRebate);
            return new AppliedRebateResponse(
                    order.getOrderNumber(),
                    null,
                    null,
                    BigDecimal.ZERO,
                    amountBeforeRebate,
                    order.getTotalAmount(),
                    order.getPaymentFee(),
                    order.getPaymentFeeTax(),
                    order.getPaymentFeeRate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateApplicationService.class, "remove(String)");
        }
    }

    // =========================================================
    // ORDER VALIDATION
    // =========================================================
    /**
     * Validates order mutable.
     *
     * @param order the order
     */
    private void validateOrderMutable(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateApplicationService.class, "validateOrderMutable(Order)");
        try {
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new IllegalStateException("Rebate can only be changed before payment.");
            }
            if (paymentRepository.existsByOrderId(order.getId())) {
                throw new IllegalStateException(
                        "Rebate cannot be changed after payment processing has started.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "validateOrderMutable(Order)");
        }
    }

    // =========================================================
    // AMOUNT
    // =========================================================
    /**
     * Calculates amount before rebate.
     *
     * @param order the order
     * @return the calculate amount before rebate result
     */
    private BigDecimal calculateAmountBeforeRebate(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class, "calculateAmountBeforeRebate(Order)");
        try {
            return defaultZero(order.getSubtotal())
                    .add(defaultZero(order.getTaxAmount()))
                    .add(defaultZero(order.getPriorityCharge()))
                    .add(defaultZero(order.getConvenienceFee()))
                    .add(defaultZero(order.getDeliveryFee()))
                    .subtract(defaultZero(order.getLoyaltyDiscount()))
                    .setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "calculateAmountBeforeRebate(Order)");
        }
    }

    /**
     * Defaults zero.
     *
     * @param amount the amount
     * @return the default zero result
     */
    private BigDecimal defaultZero(BigDecimal amount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateApplicationService.class, "defaultZero(BigDecimal)");
        try {
            return amount == null ? BigDecimal.ZERO : amount;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "defaultZero(BigDecimal)");
        }
    }

    /**
     * Performs the revalidate applied rebate before payment operation for rebate application
     * service.
     *
     * @param order the order
     */
    @Transactional
    public void revalidateAppliedRebateBeforePayment(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class,
                        "revalidateAppliedRebateBeforePayment(Order)");
        try {
            if (order.getRebate() == null
                    || order.getRebateCode() == null
                    || order.getRebateDiscountAmount() == null
                    || order.getRebateDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            try {
                AvailableRebateResponse available =
                        rebateEligibilityService.getEligibleRebate(order, order.getRebateCode());
                BigDecimal expectedAmount =
                        available.rebateAmount().setScale(2, RoundingMode.HALF_UP);
                if (expectedAmount.compareTo(order.getRebateDiscountAmount()) != 0) {
                    throw new IllegalStateException("Applied rebate amount is no longer valid.");
                }
            } catch (RuntimeException ex) {
                removeAppliedRebateInternally(order);
                throw new IllegalStateException(
                        "Applied rebate is no longer valid. Please review the updated order total"
                                + " before payment.",
                        ex);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "revalidateAppliedRebateBeforePayment(Order)");
        }
    }

    /**
     * Removes applied rebate internally.
     *
     * @param order the order
     */
    private void removeAppliedRebateInternally(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateApplicationService.class, "removeAppliedRebateInternally(Order)");
        try {
            BigDecimal amountBeforeRebate = calculateAmountBeforeRebate(order);
            order.setRebateManualSelection(false);
            order.setRebate(null);
            order.setRebateCode(null);
            order.setRebateDiscountAmount(BigDecimal.ZERO);
            order.setTotalAmount(amountBeforeRebate);
            com.gokulsweets.restaurant.order.service.PaymentFeePricing.reprice(order);
            orderRepository.save(order);
            log.info(
                    "Invalid rebate removed before payment: orderNumber={}, restoredTotal={}",
                    order.getOrderNumber(),
                    order.getTotalAmount(),
                    order.getPaymentFee(),
                    order.getPaymentFeeTax(),
                    order.getPaymentFeeRate());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateApplicationService.class,
                    "removeAppliedRebateInternally(Order)");
        }
    }
}
