package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class MealPolicyTest {
    static BigDecimal n(String x) {
        return new BigDecimal(x);
    }

    static Map<Long, Product> products() {
        return Map.of(
                1L,
                new Product(1, "Main", "UNIT", n("100.00"), 250, 50, true),
                2L,
                new Product(2, "Side", "UNIT", n("50.00"), 250, 50, true),
                3L,
                new Product(3, "Sweet", "WEIGHT", n("200.00"), 250, 50, true));
    }

    static Definition meal() {
        return new Definition(
                1,
                1,
                2,
                "Lunch",
                "दोपहर का भोजन",
                null,
                null,
                n("130.00"),
                "FIXED",
                n("10.00"),
                n("75.00"),
                true,
                List.of(
                        new Group(
                                "main",
                                "Choose main",
                                null,
                                1,
                                1,
                                List.of(new Option("main1", 1, 1, null, n("0.00")))),
                        new Group(
                                "side",
                                "Choose side",
                                null,
                                1,
                                1,
                                List.of(new Option("side1", 2, 1, null, n("0.00")))),
                        new Group(
                                "sweet",
                                "Add sweet",
                                null,
                                0,
                                1,
                                List.of(new Option("sweet1", 3, 1, 250, n("20.00"))))));
    }

    static Claim claim(int quantity, String... keys) {
        return new Claim("instance", 1, 2, quantity, List.of(keys));
    }

    static Definition discount(String type, String value) {
        var m = meal();
        return new Definition(
                m.id(),
                m.branchId(),
                m.version(),
                m.title(),
                m.titleHi(),
                m.description(),
                m.imageUrl(),
                m.basePrice(),
                type,
                n(value),
                m.estimatedCost(),
                m.active(),
                m.groups());
    }

    @Test
    void pricesRequiredChoicesAndOptionalPaidUpgradeAtExactQuantities() {
        MealPolicy.validate(meal(), products());
        var q = MealPolicy.quote(meal(), claim(2, "main1", "side1", "sweet1"), products());
        assertThat(q.total()).isEqualByComparingTo("280.00");
        assertThat(q.regularTotal()).isEqualByComparingTo("400.00");
        assertThat(q.discount()).isEqualByComparingTo("20.00");
        assertThat(q.upgrades()).isEqualByComparingTo("40.00");
        assertThat(q.components().get(2).weightGrams()).isEqualTo(500);
    }

    @Test
    void validatesFixedPercentageAndNoDiscount() {
        assertThat(MealPolicy.base(discount("PERCENT", "12.25"))).isEqualByComparingTo("114.07");
        assertThat(MealPolicy.base(discount("NONE", "0.00"))).isEqualByComparingTo("130.00");
        for (var bad :
                List.of(
                        discount("PERCENT", "100.00"),
                        discount("FIXED", "130.00"),
                        discount("FIXED", "-1.00"),
                        discount("NONE", "1.00"),
                        discount("OTHER", "0.00")))
            assertThatThrownBy(() -> MealPolicy.base(bad))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownDuplicateMissingAndStaleChoices() {
        for (var c :
                List.of(
                        claim(1, "main1"),
                        claim(1, "main1", "side1", "forged"),
                        claim(1, "main1", "side1", "side1"),
                        claim(0, "main1", "side1"),
                        new Claim("instance", 1, 1, 1, List.of("main1", "side1")),
                        new Claim("instance", 2, 2, 1, List.of("main1", "side1"))))
            assertThatThrownBy(() -> MealPolicy.quote(meal(), c, products()))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnavailableForeignAndInvalidWeightPortions() {
        var product = products().get(3L);
        assertThatThrownBy(() -> MealPolicy.portion(new Option("s", 3, 1, 200, n("0.00")), product))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MealPolicy.portion(new Option("s", 3, 1, 275, n("0.00")), product))
                .isInstanceOf(IllegalArgumentException.class);
        var map = new HashMap<>(products());
        map.remove(1L);
        assertThatThrownBy(() -> MealPolicy.quote(meal(), claim(1, "main1", "side1"), map))
                .isInstanceOf(IllegalArgumentException.class);
        map.put(1L, new Product(1, "Main", "UNIT", n("100.00"), 250, 50, false));
        assertThatThrownBy(() -> MealPolicy.quote(meal(), claim(1, "main1", "side1"), map))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMisleadingOrOverPrecisePricing() {
        assertThatThrownBy(() -> MealPolicy.money(n("1.001")))
                .isInstanceOf(IllegalArgumentException.class);
        var m = meal();
        var expensive =
                new Definition(
                        m.id(),
                        1,
                        2,
                        m.title(),
                        null,
                        null,
                        null,
                        n("200.00"),
                        "NONE",
                        n("0.00"),
                        null,
                        true,
                        m.groups());
        assertThatThrownBy(() -> MealPolicy.validate(expensive, products()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> MealPolicy.quote(expensive, claim(1, "main1", "side1"), products()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deepDiscountsOnTinyComponentsAllocateWithoutNegativeLines() {
        var parts =
                java.util.stream.LongStream.range(1, 31)
                        .mapToObj(id -> new Component(id, 1, null, n("0.01")))
                        .toList();
        var quote =
                new Quote(
                        1,
                        2,
                        "Tiny test",
                        1,
                        n("0.30"),
                        n("0.30"),
                        n("0.29"),
                        n("0.00"),
                        n("0.01"),
                        parts);
        var allocated = MealPolicy.allocations(quote);
        assertThat(allocated.stream().reduce(n("0.00"), BigDecimal::add))
                .isEqualByComparingTo("0.29");
        assertThat(allocated).allSatisfy(a -> assertThat(a).isBetween(n("0.00"), n("0.01")));
    }

    @Test
    void fractionalPaisaMealWeightsAreRejectedBeforeTheyCanChangeCombinedCartTotals() {
        var p = new Product(3, "Sweet", "WEIGHT", n("199.99"), 250, 50, true);
        assertThatThrownBy(() -> MealPolicy.portion(new Option("s", 3, 1, 250, n("0.00")), p))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MealPolicy.portion(new Option("s", 3, 1, 1000, n("0.00")), p))
                .isEqualByComparingTo("199.99");
    }
}
