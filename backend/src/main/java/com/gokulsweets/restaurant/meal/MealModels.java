package com.gokulsweets.restaurant.meal;

import java.math.BigDecimal;
import java.util.List;

/** Versioned meal definitions and server-priced component selections. */
public final class MealModels {
    /** An existing branch SKU and an explicitly included retail-valid portion. */
    public record Option(
            String key, long productId, int quantity, Integer weightGrams, BigDecimal upgrade) {}

    /** Bounded required/optional choice group. */
    public record Group(
            String key,
            String title,
            String titleHi,
            int minimum,
            int maximum,
            List<Option> options) {}

    /** Owner-managed offer; estimatedCost is redacted from public reads. */
    public record Definition(
            long id,
            long branchId,
            long version,
            String title,
            String titleHi,
            String description,
            String imageUrl,
            BigDecimal basePrice,
            String discountType,
            BigDecimal discountValue,
            BigDecimal estimatedCost,
            boolean active,
            List<Group> groups,
            String pricingMode,
            BigDecimal maximumDiscount) {
        public Definition {
            pricingMode = pricingMode == null ? "FIXED" : pricingMode;
        }

        /** Existing definitions retain their fixed-base pricing. */
        public Definition(
                long id,
                long branchId,
                long version,
                String title,
                String titleHi,
                String description,
                String imageUrl,
                BigDecimal basePrice,
                String discountType,
                BigDecimal discountValue,
                BigDecimal estimatedCost,
                boolean active,
                List<Group> groups) {
            this(
                    id,
                    branchId,
                    version,
                    title,
                    titleHi,
                    description,
                    imageUrl,
                    basePrice,
                    discountType,
                    discountValue,
                    estimatedCost,
                    active,
                    groups,
                    "FIXED",
                    null);
        }
    }

    /** Optimistically versioned admin edit. */
    public record Edit(
            long version,
            String title,
            String titleHi,
            String description,
            String imageUrl,
            BigDecimal basePrice,
            String discountType,
            BigDecimal discountValue,
            BigDecimal estimatedCost,
            boolean active,
            List<Group> groups,
            String pricingMode,
            BigDecimal maximumDiscount) {
        public Edit {
            pricingMode = pricingMode == null ? "FIXED" : pricingMode;
        }

        /** Existing API clients retain fixed-base semantics. */
        public Edit(
                long version,
                String title,
                String titleHi,
                String description,
                String imageUrl,
                BigDecimal basePrice,
                String discountType,
                BigDecimal discountValue,
                BigDecimal estimatedCost,
                boolean active,
                List<Group> groups) {
            this(
                    version,
                    title,
                    titleHi,
                    description,
                    imageUrl,
                    basePrice,
                    discountType,
                    discountValue,
                    estimatedCost,
                    active,
                    groups,
                    "FIXED",
                    null);
        }
    }

    /** Untrusted choice identity. Prices never come from this request. */
    public record Claim(
            String instanceId, long mealId, long version, int quantity, List<String> optionIds) {}

    /** Trusted branch product facts used to price and validate a recipe. */
    public record Product(
            long id,
            String name,
            String saleMode,
            BigDecimal price,
            int minimumGrams,
            int stepGrams,
            boolean available) {}

    /** Exact component demand for a complete meal selection. */
    public record Component(
            long productId,
            int quantity,
            Integer weightGrams,
            BigDecimal regularTotal,
            BigDecimal unitPrice) {
        /** Preserves allocation-only component callers. */
        public Component(
                long productId, int quantity, Integer weightGrams, BigDecimal regularTotal) {
            this(productId, quantity, weightGrams, regularTotal, null);
        }
    }

    /** Immutable packing facts; historical portions never depend on a later recipe edit. */
    public record ComponentSnapshot(
            String instanceId,
            long mealId,
            long version,
            String title,
            int mealQuantity,
            List<String> optionIds,
            long productId,
            String productName,
            int quantity,
            Integer weightGrams,
            BigDecimal unitPrice,
            BigDecimal regularTotal,
            BigDecimal mealDiscount) {}

    /** Transparent meal breakdown before item taxes and checkout charges. */
    public record Quote(
            long mealId,
            long version,
            String title,
            int quantity,
            BigDecimal regularTotal,
            BigDecimal baseTotal,
            BigDecimal discount,
            BigDecimal upgrades,
            BigDecimal total,
            List<Component> components) {}
}
