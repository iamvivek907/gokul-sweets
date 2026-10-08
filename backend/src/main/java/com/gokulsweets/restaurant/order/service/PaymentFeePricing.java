package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rate is customer-visible and inclusive of the configured tax treatment, never fee-on-fee. */
public final class PaymentFeePricing {

    /** Creates a payment fee pricing instance. */
    private PaymentFeePricing() {}

    /**
     * Zeros the operation.
     *
     * @param value the value
     * @return the zero result
     */
    private static BigDecimal zero(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentFeePricing.class, "zero(BigDecimal)");
        try {
            return value == null ? BigDecimal.ZERO : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentFeePricing.class, "zero(BigDecimal)");
        }
    }

    /**
     * Fees the operation.
     *
     * @param base the base
     * @param rate the rate
     * @return the fee result
     */
    public static BigDecimal fee(BigDecimal base, BigDecimal rate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentFeePricing.class, "fee(BigDecimal,BigDecimal)");
        try {
            return base.max(BigDecimal.ZERO)
                    .multiply(rate == null ? BigDecimal.ZERO : rate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentFeePricing.class,
                    "fee(BigDecimal,BigDecimal)");
        }
    }

    /**
     * Taxs the operation.
     *
     * @param fee the fee
     * @param rate the rate
     * @return the tax result
     */
    public static BigDecimal tax(BigDecimal fee, BigDecimal rate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentFeePricing.class, "tax(BigDecimal,BigDecimal)");
        try {
            return fee.subtract(
                    fee.multiply(new BigDecimal("100"))
                            .divide(
                                    new BigDecimal("100")
                                            .add(rate == null ? BigDecimal.ZERO : rate),
                                    2,
                                    RoundingMode.HALF_UP));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentFeePricing.class,
                    "tax(BigDecimal,BigDecimal)");
        }
    }

    /**
     * Totals with fee.
     *
     * @param order the order
     * @param base the base
     * @return the total with fee result
     */
    public static BigDecimal totalWithFee(Order order, BigDecimal base) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentFeePricing.class, "totalWithFee(Order,BigDecimal)");
        try {
            return base.add(fee(base, order.getPaymentFeeRate())).setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentFeePricing.class,
                    "totalWithFee(Order,BigDecimal)");
        }
    }

    /**
     * Reprices the operation.
     *
     * @param order the order
     */
    public static void reprice(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentFeePricing.class, "reprice(Order)");
        try {
            BigDecimal base =
                    zero(order.getSubtotal())
                            .add(zero(order.getTaxAmount()))
                            .add(zero(order.getPriorityCharge()))
                            .add(zero(order.getConvenienceFee()))
                            .add(zero(order.getDeliveryFee()))
                            .subtract(zero(order.getLoyaltyDiscount()))
                            .subtract(
                                    order.getRebateDiscountAmount() == null
                                            ? BigDecimal.ZERO
                                            : order.getRebateDiscountAmount())
                            .max(BigDecimal.ZERO);
            order.setPaymentFee(fee(base, order.getPaymentFeeRate()));
            order.setPaymentFeeTax(tax(order.getPaymentFee(), order.getPaymentFeeTaxRate()));
            order.setTotalAmount(base.add(order.getPaymentFee()).setScale(2, RoundingMode.HALF_UP));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentFeePricing.class, "reprice(Order)");
        }
    }
}
