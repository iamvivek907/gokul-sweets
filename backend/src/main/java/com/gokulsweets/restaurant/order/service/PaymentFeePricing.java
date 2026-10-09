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
     * Returns zero information for payment fee pricing.
     *
     * @param value the value supplied to this method
     * @return the value of {@code value == null ? BigDecimal.ZERO : value}
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
     * Returns fee information for payment fee pricing.
     *
     * @param base the base supplied to this method
     * @param rate the rate supplied to this method
     * @return the {@code BigDecimal} result
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
     * Returns tax information for payment fee pricing.
     *
     * @param fee the fee supplied to this method
     * @param rate the rate supplied to this method
     * @return the {@code BigDecimal} result
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
     * Returns reprice information for payment fee pricing.
     *
     * @param order the order supplied to this method
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
