package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.product.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Offer-specific product spend. Fees and item tax never fund a restricted offer. */
final class RebateSpendBasis {
    private RebateSpendBasis() {}

    /** Reports whether this offer uses product/category exclusions. */
    static boolean restricted(Rebate rebate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateSpendBasis.class, "restricted(Rebate)");
        try {
            return !rebate.getExcludedProductIds().isEmpty()
                    || !rebate.getExcludedCategoryIds().isEmpty();

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateSpendBasis.class, "restricted(Rebate)");
        }
    }

    /** Checks the union of item and category rules; unknown product identity fails closed. */
    static boolean includes(Rebate rebate, Product product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateSpendBasis.class, "includes(Rebate,Product)");
        try {
            if (product == null || product.getId() == null) return false;
            if (rebate.getExcludedProductIds().contains(product.getId())) return false;
            if (rebate.getExcludedCategoryIds().isEmpty()) return true;
            // Unknown classification cannot bypass a category exclusion.
            return product.getCategory() != null
                    && product.getCategory().getId() != null
                    && !rebate.getExcludedCategoryIds().contains(product.getCategory().getId());

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateSpendBasis.class, "includes(Rebate,Product)");
        }
    }

    /**
     * Sums eligible priced lines, excluding tax, after proportional order-level loyalty savings.
     */
    static BigDecimal eligible(Order order, Rebate rebate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateSpendBasis.class, "eligible(Order,Rebate)");
        try {
            BigDecimal goods = BigDecimal.ZERO;
            BigDecimal included = BigDecimal.ZERO;
            for (var item : order.getItems()) {
                // lineTotal already contains the quantity/weight and includes item tax.
                BigDecimal subtotal =
                        zero(item.getLineTotal())
                                .subtract(zero(item.getTaxAmount()))
                                .max(BigDecimal.ZERO);
                goods = goods.add(subtotal);
                if (includes(rebate, item.getProduct())) included = included.add(subtotal);
            }
            if (goods.signum() <= 0 || included.signum() <= 0) return BigDecimal.ZERO.setScale(2);
            // Allocate an order-level loyalty discount proportionally across all product spend.
            BigDecimal discount =
                    order.isLoyaltyEnrolled()
                            ? zero(order.getLoyaltyDiscount()).max(BigDecimal.ZERO).min(goods)
                            : BigDecimal.ZERO;
            return included.multiply(goods.subtract(discount))
                    .divide(goods, 2, RoundingMode.HALF_UP);

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateSpendBasis.class, "eligible(Order,Rebate)");
        }
    }

    /**
     * Finds the smallest additional eligible product subtotal that reaches a tier, assuming the
     * current loyalty discount is retained. Adding eligible goods changes their share of that
     * discount, so the net eligibility shortfall is not the amount of goods to add. Checkout
     * revalidates the actual products, taxes and reward selection.
     */
    static BigDecimal additionalSpend(Order order, Rebate rebate, BigDecimal threshold) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateSpendBasis.class, "additionalSpend(Order,Rebate,BigDecimal)");
        try {
            BigDecimal goods = BigDecimal.ZERO;
            BigDecimal included = BigDecimal.ZERO;
            for (var item : order.getItems()) {
                BigDecimal subtotal =
                        zero(item.getLineTotal())
                                .subtract(zero(item.getTaxAmount()))
                                .max(BigDecimal.ZERO);
                goods = goods.add(subtotal);
                if (includes(rebate, item.getProduct())) included = included.add(subtotal);
            }
            BigDecimal discount =
                    order.isLoyaltyEnrolled()
                            ? zero(order.getLoyaltyDiscount()).max(BigDecimal.ZERO).min(goods)
                            : BigDecimal.ZERO;
            if (eligible(order, rebate).compareTo(threshold) >= 0)
                return BigDecimal.ZERO.setScale(2);
            // Even allocating the entire discount to eligible goods cannot require more than this.
            var high =
                    threshold
                            .add(discount)
                            .subtract(included)
                            .max(BigDecimal.ZERO)
                            .movePointRight(2)
                            .setScale(0, RoundingMode.CEILING)
                            .toBigIntegerExact();
            var low = java.math.BigInteger.ZERO;
            while (low.compareTo(high) < 0) {
                var mid = low.add(high).shiftRight(1);
                var addition = new BigDecimal(mid, 2);
                var total = goods.add(addition);
                var projected =
                        total.signum() == 0
                                ? BigDecimal.ZERO
                                : included.add(addition)
                                        .multiply(total.subtract(discount))
                                        .divide(total, 2, RoundingMode.HALF_UP);
                if (projected.compareTo(threshold) >= 0) high = mid;
                else low = mid.add(java.math.BigInteger.ONE);
            }
            return new BigDecimal(high, 2);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateSpendBasis.class,
                    "additionalSpend(Order,Rebate,BigDecimal)");
        }
    }

    /** Adds customer-facing exclusion terms to the stored offer description. */
    static String description(Rebate rebate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateSpendBasis.class, "description(Rebate)");
        try {
            String original = rebate.getDescription();
            if (!restricted(rebate)) return original;
            String terms =
                    "Selected categories or items are excluded. Only eligible product spend after"
                            + " loyalty savings counts; taxes and fees do not count.";
            return original == null || original.isBlank() ? terms : original + " " + terms;

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateSpendBasis.class, "description(Rebate)");
        }
    }

    /** Treats absent monetary snapshots as zero. */
    private static BigDecimal zero(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateSpendBasis.class, "zero(BigDecimal)");
        try {
            return value == null ? BigDecimal.ZERO : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateSpendBasis.class, "zero(BigDecimal)");
        }
    }
}
