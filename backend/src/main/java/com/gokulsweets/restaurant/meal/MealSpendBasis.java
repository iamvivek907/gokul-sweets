package com.gokulsweets.restaurant.meal;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;

import java.math.BigDecimal;

/**
 * Trusted persisted meal allocations separate extra quantities of the same SKU without DB reads.
 */
public final class MealSpendBasis {
    private static final tools.jackson.databind.ObjectMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private MealSpendBasis() {}

    public static boolean hasMeals(Order order) {
        return order.getItems().stream().anyMatch(i -> i.getMealLabel() != null);
    }

    public static BigDecimal standaloneSubtotal(OrderItem item) {
        BigDecimal subtotal =
                item.getLineTotal()
                        .subtract(
                                item.getTaxAmount() == null ? BigDecimal.ZERO : item.getTaxAmount())
                        .max(BigDecimal.ZERO);
        if (item.getMealLabel() == null) return subtotal;
        // Old reservations lacking complete packing facts cannot fund a new offer.
        if (item.getMealDetails() == null || item.getMealDetails().isBlank())
            return BigDecimal.ZERO;
        try {
            var parts = JSON.readValue(item.getMealDetails(), MealModels.ComponentSnapshot[].class);
            if (parts.length == 0) return BigDecimal.ZERO;
            BigDecimal included = BigDecimal.ZERO;
            for (var part : parts) {
                if (part.regularTotal() == null
                        || part.mealDiscount() == null
                        || part.regularTotal().signum() < 0
                        || part.mealDiscount().signum() < 0
                        || part.mealDiscount().compareTo(part.regularTotal()) > 0)
                    return BigDecimal.ZERO;
                included = included.add(part.regularTotal().subtract(part.mealDiscount()));
            }
            return subtotal.subtract(included).max(BigDecimal.ZERO);
        } catch (RuntimeException invalid) {
            return BigDecimal.ZERO;
        }
    }
}
