package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealPolicyTest.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.OrderCalculationService;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.*;

class MealPricingTest {
    ValidatedOrderItem item(long id, String price, int quantity) {
        var p = new Product();
        p.setId(id);
        p.setName("Dish " + id);
        p.setBasePrice(n(price));
        p.setSaleMode(ProductSaleMode.UNIT);
        var bp = new BranchProduct();
        bp.setProduct(p);
        return new ValidatedOrderItem(p, bp, ProductSaleMode.UNIT, quantity, null);
    }

    @Test
    void allocatesExactSavingsAcrossRealDemandWithoutChangingInventoryQuantities() {
        var service = mock(MealService.class);
        when(service.catalog(1, false)).thenReturn(List.of(meal()));
        var pricing = new MealPricing(service, JsonMapper.builder().build());
        var raw =
                List.of(
                        new CreateOrderItemRequest(
                                1L, 2, null, List.of(claim(1, "main1", "side1"))),
                        new CreateOrderItemRequest(2L, 1, null));
        var result = pricing.apply(1, raw, List.of(item(1, "100.00", 2), item(2, "50.00", 1)));
        assertThat(result.get(0).quantity()).isEqualTo(2);
        assertThat(result.get(0).mealDiscount()).isEqualByComparingTo("20.00");
        assertThat(result.get(1).mealDiscount()).isEqualByComparingTo("10.00");
        assertThat(result.get(0).mealDetails()).contains("main1");
        var total = new OrderCalculationService().calculateDelivery(result);
        assertThat(total.subtotal()).isEqualByComparingTo("220.00");
    }

    @Test
    void rejectsReusingSameComponentsForTwoMealsAndDuplicateIdentities() {
        var service = mock(MealService.class);
        when(service.catalog(1, false)).thenReturn(List.of(meal()));
        var pricing = new MealPricing(service, JsonMapper.builder().build());
        var second = new MealModels.Claim("other", 1, 2, 1, List.of("main1", "side1"));
        for (var claims :
                List.of(
                        List.of(claim(1, "main1", "side1"), second),
                        List.of(claim(1, "main1", "side1"), claim(1, "main1", "side1"))))
            assertThatThrownBy(
                            () ->
                                    pricing.apply(
                                            1,
                                            List.of(
                                                    new CreateOrderItemRequest(1L, 1, null, claims),
                                                    new CreateOrderItemRequest(2L, 1, null)),
                                            List.of(item(1, "100.00", 1), item(2, "50.00", 1))))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordinaryOrdersPerformNoMealReads() {
        var service = mock(MealService.class);
        var items = List.of(item(1, "100.00", 1));
        assertThat(
                        new MealPricing(service, JsonMapper.builder().build())
                                .apply(1, List.of(new CreateOrderItemRequest(1L, 1, null)), items))
                .isSameAs(items);
        verifyNoInteractions(service);
    }

    @Test
    void paisaAllocationNeverExceedsTheOriginalLine() {
        var service = mock(MealService.class);
        var m = meal();
        var def =
                new MealModels.Definition(
                        m.id(),
                        1,
                        2,
                        m.title(),
                        null,
                        null,
                        null,
                        n("149.99"),
                        "NONE",
                        n("0.00"),
                        null,
                        true,
                        m.groups());
        when(service.catalog(1, false)).thenReturn(List.of(def));
        var pricing = new MealPricing(service, JsonMapper.builder().build());
        var lines =
                pricing.apply(
                        1,
                        List.of(
                                new CreateOrderItemRequest(
                                        1L, 1, null, List.of(claim(1, "main1", "side1"))),
                                new CreateOrderItemRequest(2L, 1, null)),
                        List.of(item(1, "100.00", 1), item(2, "50.00", 1)));
        assertThat(
                        lines.stream()
                                .map(ValidatedOrderItem::mealDiscount)
                                .reduce(n("0.00"), java.math.BigDecimal::add))
                .isEqualByComparingTo("0.01");
        assertThat(new OrderCalculationService().calculateDelivery(lines).subtotal())
                .isEqualByComparingTo("149.99");
    }

    @Test
    void snapshotsAndPackingLabelsSeparateSharedWeightedAndUnitComponents() {
        var service = mock(MealService.class);
        when(service.catalog(1, false)).thenReturn(List.of(meal()));
        var pricing = new MealPricing(service, JsonMapper.builder().build());
        var first = new MealModels.Claim("first", 1, 2, 1, List.of("main1", "side1", "sweet1"));
        var second = new MealModels.Claim("second", 1, 2, 2, List.of("main1", "side1", "sweet1"));
        var sweet = item(3, "200.00", 1);
        sweet.product().setSaleMode(ProductSaleMode.WEIGHT);
        sweet.product().setMinimumWeightGrams(250);
        sweet.product().setWeightStepGrams(50);
        var weighted =
                new ValidatedOrderItem(
                        sweet.product(), sweet.branchProduct(), ProductSaleMode.WEIGHT, 1, 1000);
        var result =
                pricing.apply(
                        1,
                        List.of(
                                new CreateOrderItemRequest(1L, 3, null, List.of(first, second)),
                                new CreateOrderItemRequest(2L, 3, null),
                                new CreateOrderItemRequest(3L, null, 1000)),
                        List.of(item(1, "100.00", 3), item(2, "50.00", 3), weighted));
        assertThat(result.get(0).mealLabel())
                .contains("#first ×1 (1 units)", "#second ×2 (2 units)");
        assertThat(result.get(2).mealLabel()).contains("#first ×1 (250 g)", "#second ×2 (500 g)");
        assertThat(result.get(2).weightGrams()).isEqualTo(1000);
        assertThat(result.get(2).mealDetails())
                .contains(
                        "\"weightGrams\":250",
                        "\"weightGrams\":500",
                        "\"mealQuantity\":2",
                        "\"productName\":\"Dish 3\"",
                        "\"title\":\"Lunch\"",
                        "\"version\":2",
                        "\"unitPrice\":200",
                        "\"regularTotal\":50",
                        "\"mealDiscount\":");
        // Later recipe edits cannot change the stored names, portions or allocations.
        when(service.catalog(1, false)).thenReturn(List.of());
        assertThat(result.get(2).mealDetails())
                .contains("\"title\":\"Lunch\"", "\"weightGrams\":250");
    }
}
