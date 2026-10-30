package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;
import static com.gokulsweets.restaurant.meal.MealPolicyTest.*;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class SelectedMealPolicyTest {
    Definition automatic(String type, String value, String cap) {
        var m = meal();
        var groups =
                m.groups().stream()
                        .map(
                                g ->
                                        new Group(
                                                g.key(),
                                                g.title(),
                                                g.titleHi(),
                                                g.minimum(),
                                                g.maximum(),
                                                g.options().stream()
                                                        .map(
                                                                o ->
                                                                        new Option(
                                                                                o.key(),
                                                                                o.productId(),
                                                                                o.quantity(),
                                                                                o.weightGrams(),
                                                                                n("0.00")))
                                                        .toList()))
                        .toList();
        return new Definition(
                m.id(),
                m.branchId(),
                m.version(),
                m.title(),
                m.titleHi(),
                m.description(),
                m.imageUrl(),
                n("0.00"),
                type,
                n(value),
                null,
                true,
                groups,
                "SELECTED_ITEMS",
                cap == null ? null : n(cap));
    }

    @Test
    void selectedDishesDetermineTheTotalWithTwoPercentOff() {
        var m = automatic("PERCENT", "2.00", null);
        MealPolicy.validate(m, products());
        var q = MealPolicy.quote(m, claim(1, "main1", "side1", "sweet1"), products());
        assertThat(q.regularTotal()).isEqualByComparingTo("200.00");
        assertThat(q.total()).isEqualByComparingTo("196.00");
        assertThat(q.upgrades()).isEqualByComparingTo("0.00");
        assertThat(MealPolicy.quote(m, claim(1, "main1", "side1"), products()).total())
                .isEqualByComparingTo("147.00");
    }

    @Test
    void discountCapAndRoundingApplyToEachMealBeforeQuantity() {
        var m = automatic("PERCENT", "10.00", "12.00");
        var q = MealPolicy.quote(m, claim(3, "main1", "side1", "sweet1"), products());
        assertThat(q.discount()).isEqualByComparingTo("36.00");
        assertThat(q.total()).isEqualByComparingTo("564.00");
        var map = new HashMap<>(products());
        map.put(1L, new Product(1, "Main", "UNIT", n("100.25"), 250, 50, true));
        assertThat(
                        MealPolicy.quote(
                                        automatic("PERCENT", "2.00", null),
                                        claim(3, "main1", "side1"),
                                        map)
                                .total())
                .isEqualByComparingTo("441.72");
    }

    @Test
    void currentBranchPricesAndWeightPortionsRemainAuthoritative() {
        var m = automatic("FIXED", "5.00", null);
        var map = new HashMap<>(products());
        map.put(1L, new Product(1, "Main", "UNIT", n("120.00"), 250, 50, true));
        var q = MealPolicy.quote(m, claim(1, "main1", "side1", "sweet1"), map);
        assertThat(q.total()).isEqualByComparingTo("215.00");
        assertThat(q.components().get(2).weightGrams()).isEqualTo(250);
        assertThat(MealPolicy.allocations(q).stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void unsafeDiscountsAndZeroOrNegativeCapsAreRejected() {
        for (var m :
                List.of(
                        automatic("PERCENT", "100.00", null),
                        automatic("FIXED", "150.00", null),
                        automatic("PERCENT", "2.00", "0.00"),
                        automatic("PERCENT", "2.00", "-1.00")))
            assertThatThrownBy(() -> MealPolicy.validate(m, products()))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingChoicesUnavailableDishesAndStaleRecipesAreRejected() {
        var m = automatic("PERCENT", "2.00", null);
        assertThatThrownBy(() -> MealPolicy.quote(m, claim(1, "main1"), products()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                MealPolicy.quote(
                                        m,
                                        new Claim("instance", 1, 1, 1, List.of("main1", "side1")),
                                        products()))
                .isInstanceOf(IllegalArgumentException.class);
        var map = new HashMap<>(products());
        map.put(1L, new Product(1, "Main", "UNIT", n("100.00"), 250, 50, false));
        assertThatThrownBy(() -> MealPolicy.quote(m, claim(1, "main1", "side1"), map))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyMealsKeepTheirFixedBaseAndUpgrades() {
        assertThat(meal().pricingMode()).isEqualTo("FIXED");
        assertThat(
                        MealPolicy.quote(meal(), claim(1, "main1", "side1", "sweet1"), products())
                                .total())
                .isEqualByComparingTo("140.00");
    }
}
