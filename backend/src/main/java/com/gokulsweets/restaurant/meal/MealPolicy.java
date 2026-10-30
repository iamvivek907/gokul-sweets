package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.*;
import java.util.*;

/** Pure server-side recipe, discount and portion validation. */
public final class MealPolicy {
    /** Validates exact paisa values without trusting client rounding. */
    public static BigDecimal money(BigDecimal amount) {
        long started = MethodTiming.start(MealPolicy.class, "money(BigDecimal)");
        try {
            if (amount == null
                    || amount.scale() > 2
                    || amount.abs().compareTo(new BigDecimal("1000000")) > 0)
                throw new IllegalArgumentException(
                        "Use a price with at most two decimal places, below 1000000.");
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } finally {
            MethodTiming.finish(started, MealPolicy.class, "money(BigDecimal)");
        }
    }

    /** Calculates the owner-selected discounted base; upgrades are charged separately. */
    public static BigDecimal base(Definition meal) {
        long started = MethodTiming.start(MealPolicy.class, "base(Definition)");
        try {
            if (meal.discountType() == null)
                throw new IllegalArgumentException("Choose a meal discount type.");
            BigDecimal price = money(meal.basePrice());
            return price.subtract(discount(meal, price));
        } finally {
            MethodTiming.finish(started, MealPolicy.class, "base(Definition)");
        }
    }

    /** Exact per-meal discount against its selected price basis, optionally capped. */
    public static BigDecimal discount(Definition meal, BigDecimal price) {
        long started = MethodTiming.start(MealPolicy.class, "discount(Definition,BigDecimal)");
        try {
            if (meal.discountType() == null)
                throw new IllegalArgumentException("Choose a meal discount type.");
            BigDecimal value = money(meal.discountValue());
            if (price.signum() <= 0 || value.signum() < 0)
                throw new IllegalArgumentException(
                        "Meal prices must be positive and discounts nonnegative.");
            BigDecimal discount =
                    switch (meal.discountType()) {
                        case "NONE" -> {
                            if (value.signum() != 0)
                                throw new IllegalArgumentException(
                                        "No-discount value must be zero.");
                            yield BigDecimal.ZERO;
                        }
                        case "FIXED" -> value;
                        case "PERCENT" -> {
                            if (value.compareTo(new BigDecimal("100")) >= 0)
                                throw new IllegalArgumentException(
                                        "Percentage discount must be below 100.");
                            yield price.multiply(value)
                                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        }
                        default ->
                                throw new IllegalArgumentException(
                                        "Choose NONE, FIXED or PERCENT discount.");
                    };
            if (meal.maximumDiscount() != null) {
                BigDecimal cap = money(meal.maximumDiscount());
                if (cap.signum() <= 0)
                    throw new IllegalArgumentException("Maximum discount must be positive.");
                discount = discount.min(cap);
            }
            if (discount.compareTo(price) >= 0)
                throw new IllegalArgumentException(
                        "The discount must leave a positive meal price.");
            return discount;
        } finally {
            MethodTiming.finish(started, MealPolicy.class, "discount(Definition,BigDecimal)");
        }
    }

    /** Rejects unsafe prices, incomplete groups and unsupported component portions. */
    public static void validate(Definition meal, Map<Long, Product> products) {
        long started =
                MethodTiming.start(MealPolicy.class, "validate(Definition,Map<Long,Product>)");
        try {
            if (meal.title() == null
                    || meal.title().isBlank()
                    || meal.title().length() > 80
                    || meal.titleHi() != null && meal.titleHi().length() > 120
                    || meal.description() != null && meal.description().length() > 500)
                throw new IllegalArgumentException("Check the meal name and description lengths.");
            if (meal.imageUrl() != null
                    && !meal.imageUrl().isBlank()
                    && (meal.imageUrl().length() > 600
                            || !(meal.imageUrl().startsWith("https://")
                                    || meal.imageUrl().startsWith("/")
                                            && !meal.imageUrl().startsWith("//"))))
                throw new IllegalArgumentException("Use an HTTPS or local meal image.");
            boolean automatic = "SELECTED_ITEMS".equals(meal.pricingMode());
            if (!automatic && !"FIXED".equals(meal.pricingMode()))
                throw new IllegalArgumentException("Choose a valid meal pricing mode.");
            if (automatic && (meal.basePrice() == null || money(meal.basePrice()).signum() != 0))
                throw new IllegalArgumentException("Automatic meals do not use a base price.");
            BigDecimal effective = automatic ? BigDecimal.ZERO : base(meal),
                    minimum = BigDecimal.ZERO;
            if (meal.estimatedCost() != null && money(meal.estimatedCost()).signum() < 0)
                throw new IllegalArgumentException("Estimated cost cannot be negative.");
            if (meal.groups() == null || meal.groups().isEmpty() || meal.groups().size() > 8)
                throw new IllegalArgumentException("Configure 1 to 8 meal groups.");
            Set<String> keys = new HashSet<>();
            Set<Long> ids = new HashSet<>();
            int required = 0, total = 0;
            for (Group group : meal.groups()) {
                if (group == null
                        || group.key() == null
                        || !group.key().matches("[A-Za-z0-9_-]{1,40}")
                        || !keys.add("g:" + group.key())
                        || group.title() == null
                        || group.title().isBlank()
                        || group.title().length() > 100
                        || group.titleHi() != null && group.titleHi().length() > 150)
                    throw new IllegalArgumentException(
                            "Give each group a unique key and short name.");
                if (group.options() == null
                        || group.options().isEmpty()
                        || group.options().size() > 20
                        || group.minimum() < 0
                        || group.maximum() < 1
                        || group.maximum() > 4
                        || group.maximum() > group.options().size()
                        || group.minimum() > group.maximum())
                    throw new IllegalArgumentException(
                            "Check each group's selection limits (maximum 4).");
                required += group.minimum();
                total += group.maximum();
                List<BigDecimal> values = new ArrayList<>();
                for (Option option : group.options()) {
                    if (option == null
                            || option.key() == null
                            || !option.key().matches("[A-Za-z0-9_-]{1,40}")
                            || !keys.add("o:" + option.key())
                            || !ids.add(option.productId()))
                        throw new IllegalArgumentException(
                                "Use unique option keys and one option per SKU in a meal.");
                    Product product = products.get(option.productId());
                    if (product == null)
                        throw new IllegalArgumentException(
                                "Every option must reference a product in this branch.");
                    BigDecimal regular = portion(option, product),
                            upgrade = money(option.upgrade());
                    if (upgrade.signum() < 0
                            || upgrade.compareTo(regular) > 0
                            || automatic && upgrade.signum() != 0)
                        throw new IllegalArgumentException(
                                "An upgrade must be between zero and its component price.");
                    values.add(regular.subtract(upgrade));
                }
                values.sort(Comparator.naturalOrder());
                for (int i = 0; i < group.minimum(); i++) minimum = minimum.add(values.get(i));
            }
            if (required == 0 || total > 30)
                throw new IllegalArgumentException(
                        "Require at least one dish and at most 30 choices.");
            if (automatic) discount(meal, minimum);
            if (!automatic && effective.compareTo(minimum) > 0)
                throw new IllegalArgumentException(
                        "The discounted base exceeds the cheapest valid selection. Lower the base"
                                + " or adjust required choices.");
        } finally {
            MethodTiming.finish(
                    started, MealPolicy.class, "validate(Definition,Map<Long,Product>)");
        }
    }

    /** Prices an explicit quantity using the branch SKU price and valid sale units. */
    public static BigDecimal portion(Option option, Product product) {
        long started = MethodTiming.start(MealPolicy.class, "portion(Option,Product)");
        try {
            if ("WEIGHT".equals(product.saleMode())) {
                if (option.quantity() != 1
                        || option.weightGrams() == null
                        || option.weightGrams() < product.minimumGrams()
                        || option.weightGrams() > 100000
                        || product.stepGrams() <= 0
                        || (option.weightGrams() - product.minimumGrams()) % product.stepGrams()
                                != 0
                        || option.weightGrams() % product.stepGrams() != 0)
                    throw new IllegalArgumentException(
                            "Choose a valid retail weight portion for " + product.name());
                BigDecimal portion =
                        money(product.price())
                                .multiply(BigDecimal.valueOf(option.weightGrams()))
                                .divide(new BigDecimal("1000"));
                if (portion.stripTrailingZeros().scale() > 2)
                    throw new IllegalArgumentException(
                            "Choose a meal weight with an exact paisa price for " + product.name());
                return portion.setScale(2, RoundingMode.UNNECESSARY);
            }
            if (option.quantity() < 1 || option.quantity() > 50 || option.weightGrams() != null)
                throw new IllegalArgumentException(
                        "Choose a valid item quantity for " + product.name());
            return money(product.price()).multiply(BigDecimal.valueOf(option.quantity()));
        } finally {
            MethodTiming.finish(started, MealPolicy.class, "portion(Option,Product)");
        }
    }

    /** Validates every selected option and returns exact grouped component demand and price. */
    public static Quote quote(Definition meal, Claim claim, Map<Long, Product> products) {
        long started =
                MethodTiming.start(MealPolicy.class, "quote(Definition,Claim,Map<Long,Product>)");
        try {
            if (!meal.active()
                    || claim == null
                    || claim.mealId() != meal.id()
                    || claim.version() != meal.version())
                throw new IllegalArgumentException(
                        "This meal changed or is no longer offered. Rebuild it from the menu.");
            if (claim.instanceId() == null
                    || !claim.instanceId().matches("[A-Za-z0-9_-]{1,60}")
                    || claim.quantity() < 1
                    || claim.quantity() > 10
                    || claim.optionIds() == null
                    || claim.optionIds().isEmpty()
                    || claim.optionIds().size() > 30)
                throw new IllegalArgumentException("Check meal choices and quantity (1 to 10).");
            Set<String> selected = new HashSet<>(claim.optionIds());
            if (selected.size() != claim.optionIds().size())
                throw new IllegalArgumentException("Do not repeat a meal option.");
            List<Component> components = new ArrayList<>();
            BigDecimal regular = BigDecimal.ZERO, extras = BigDecimal.ZERO;
            int found = 0;
            for (Group group : meal.groups()) {
                int chosen = 0;
                for (Option option : group.options())
                    if (selected.contains(option.key())) {
                        chosen++;
                        found++;
                        Product product = products.get(option.productId());
                        if (product == null || !product.available())
                            throw new IllegalArgumentException(
                                    "A selected meal dish is unavailable. Review the meal.");
                        if ("SELECTED_ITEMS".equals(meal.pricingMode())
                                && money(option.upgrade()).signum() != 0)
                            throw new IllegalArgumentException(
                                    "Automatic meals cannot have upgrade charges.");
                        BigDecimal amount =
                                portion(option, product)
                                        .multiply(BigDecimal.valueOf(claim.quantity()));
                        regular = regular.add(amount);
                        extras =
                                extras.add(
                                        money(option.upgrade())
                                                .multiply(BigDecimal.valueOf(claim.quantity())));
                        components.add(
                                new Component(
                                        option.productId(),
                                        Math.multiplyExact(option.quantity(), claim.quantity()),
                                        option.weightGrams() == null
                                                ? null
                                                : Math.multiplyExact(
                                                        option.weightGrams(), claim.quantity()),
                                        amount,
                                        product.price()));
                    }
                if (chosen < group.minimum() || chosen > group.maximum())
                    throw new IllegalArgumentException("Check selections for " + group.title());
            }
            if (found != selected.size())
                throw new IllegalArgumentException("A meal option does not belong to this recipe.");
            boolean automatic = "SELECTED_ITEMS".equals(meal.pricingMode());
            if (!automatic && !"FIXED".equals(meal.pricingMode()))
                throw new IllegalArgumentException("Choose a valid meal pricing mode.");
            BigDecimal fullBase =
                    automatic
                            ? regular
                            : money(meal.basePrice())
                                    .multiply(BigDecimal.valueOf(claim.quantity()));
            BigDecimal onePrice =
                    automatic
                            ? regular.divide(
                                    BigDecimal.valueOf(claim.quantity()),
                                    2,
                                    RoundingMode.UNNECESSARY)
                            : money(meal.basePrice());
            BigDecimal discounted =
                    onePrice.subtract(discount(meal, onePrice))
                            .multiply(BigDecimal.valueOf(claim.quantity()));
            BigDecimal total = discounted.add(extras);
            if (total.signum() <= 0 || total.compareTo(regular) > 0)
                throw new IllegalArgumentException(
                        "Meal pricing changed. Ask the branch to update this meal.");
            return new Quote(
                    meal.id(),
                    meal.version(),
                    meal.title(),
                    claim.quantity(),
                    regular,
                    fullBase,
                    fullBase.subtract(discounted),
                    extras,
                    total,
                    List.copyOf(components));
        } finally {
            MethodTiming.finish(
                    started, MealPolicy.class, "quote(Definition,Claim,Map<Long,Product>)");
        }
    }

    /** Allocates savings to the paisa without exceeding any component, including tiny portions. */
    public static List<BigDecimal> allocations(Quote quote) {
        long started = MethodTiming.start(MealPolicy.class, "allocations(Quote)");
        try {
            BigDecimal saving = quote.regularTotal().subtract(quote.total()), sum = BigDecimal.ZERO;
            List<BigDecimal> values = new ArrayList<>();
            for (Component c : quote.components()) {
                BigDecimal value =
                        saving.multiply(c.regularTotal())
                                .divide(quote.regularTotal(), 2, RoundingMode.DOWN);
                values.add(value);
                sum = sum.add(value);
            }
            int left = saving.subtract(sum).movePointRight(2).intValueExact();
            BigDecimal cent = new BigDecimal("0.01");
            while (left > 0) {
                boolean moved = false;
                for (int i = 0; i < values.size() && left > 0; i++)
                    if (quote.components()
                                    .get(i)
                                    .regularTotal()
                                    .subtract(values.get(i))
                                    .compareTo(cent)
                            >= 0) {
                        values.set(i, values.get(i).add(cent));
                        left--;
                        moved = true;
                    }
                if (!moved)
                    throw new IllegalArgumentException(
                            "Meal savings cannot be allocated to its components.");
            }
            return List.copyOf(values);
        } finally {
            MethodTiming.finish(started, MealPolicy.class, "allocations(Quote)");
        }
    }
}
