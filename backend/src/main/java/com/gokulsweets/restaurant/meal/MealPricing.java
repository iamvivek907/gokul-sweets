package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.math.*;
import java.util.*;

/** Revalidates recipes against actual cart demand and allocates exact pre-tax meal savings. */
@Service
@RequiredArgsConstructor
public class MealPricing {
    private final MealService meals;
    private final ObjectMapper mapper;

    /** Rejects forged, stale or incomplete meal claims; ordinary items perform no meal queries. */
    public List<ValidatedOrderItem> apply(
            long branchId, List<CreateOrderItemRequest> requested, List<ValidatedOrderItem> items) {
        long started =
                MethodTiming.start(
                        MealPricing.class,
                        "apply(long,List<CreateOrderItemRequest>,List<ValidatedOrderItem>)");
        try {
            var carriers =
                    requested.stream()
                            .filter(
                                    i ->
                                            i.mealSelections() != null
                                                    && !i.mealSelections().isEmpty())
                            .toList();
            if (carriers.isEmpty()) return items;
            if (carriers.size() != 1 || carriers.getFirst().mealSelections().size() > 10)
                throw new IllegalArgumentException(
                        "Attach up to 10 meal selections to one cart item.");
            var claims = carriers.getFirst().mealSelections();
            Set<String> instances = new HashSet<>();
            Map<Long, Product> products = new HashMap<>();
            Map<Long, Long> remaining = new HashMap<>();
            Map<Long, BigDecimal> discounts = new HashMap<>();
            Map<Long, List<String>> labels = new HashMap<>();
            Map<Long, List<ComponentSnapshot>> snapshots = new HashMap<>();
            for (var item : items) {
                var p = item.product();
                var bp = item.branchProduct();
                products.put(
                        p.getId(),
                        new Product(
                                p.getId(),
                                p.getName(),
                                item.saleMode().name(),
                                bp.getPriceOverride() != null
                                        ? bp.getPriceOverride()
                                        : p.getBasePrice(),
                                p.getMinimumWeightGrams() == null ? 250 : p.getMinimumWeightGrams(),
                                p.getWeightStepGrams() == null ? 50 : p.getWeightStepGrams(),
                                true));
                remaining.put(
                        p.getId(),
                        (long) (item.weightGrams() == null ? item.quantity() : item.weightGrams()));
            }
            var definitions = meals.catalog(branchId, false);
            for (Claim claim : claims) {
                if (claim == null || !instances.add(claim.instanceId()))
                    throw new IllegalArgumentException("Meal identities must be unique.");
                Definition definition =
                        definitions.stream()
                                .filter(m -> m.id() == claim.mealId())
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "A meal is no longer offered. Review the"
                                                                + " cart."));
                Quote quote = MealPolicy.quote(definition, claim, products);
                var allocations = MealPolicy.allocations(quote);
                for (int index = 0; index < quote.components().size(); index++) {
                    var part = quote.components().get(index);
                    long demand = part.weightGrams() == null ? part.quantity() : part.weightGrams();
                    long available = remaining.getOrDefault(part.productId(), 0L);
                    if (demand > available)
                        throw new IllegalArgumentException(
                                "Included meal dishes changed. Rebuild the meal before paying.");
                    remaining.put(part.productId(), available - demand);
                    BigDecimal amount = allocations.get(index);
                    discounts.merge(part.productId(), amount, BigDecimal::add);
                    labels.computeIfAbsent(part.productId(), k -> new ArrayList<>())
                            .add(
                                    quote.title()
                                            + " #"
                                            + claim.instanceId()
                                                    .substring(
                                                            Math.max(
                                                                    0,
                                                                    claim.instanceId().length()
                                                                            - 6))
                                            + " ×"
                                            + claim.quantity()
                                            + " ("
                                            + (part.weightGrams() == null
                                                    ? part.quantity() + " units"
                                                    : part.weightGrams() + " g")
                                            + ")");
                    snapshots
                            .computeIfAbsent(part.productId(), k -> new ArrayList<>())
                            .add(
                                    new ComponentSnapshot(
                                            claim.instanceId(),
                                            quote.mealId(),
                                            quote.version(),
                                            quote.title(),
                                            quote.quantity(),
                                            List.copyOf(claim.optionIds()),
                                            part.productId(),
                                            products.get(part.productId()).name(),
                                            part.quantity(),
                                            part.weightGrams(),
                                            part.unitPrice(),
                                            part.regularTotal(),
                                            amount));
                }
            }
            List<ValidatedOrderItem> priced = new ArrayList<>();
            for (var item : items) {
                long id = item.product().getId();
                String label = labels.containsKey(id) ? String.join("; ", labels.get(id)) : null;
                if (label != null && label.length() > 500)
                    throw new IllegalArgumentException(
                            "Too many meal labels for one dish. Use fewer meals in this order.");
                priced.add(
                        new ValidatedOrderItem(
                                item.product(),
                                item.branchProduct(),
                                item.saleMode(),
                                item.quantity(),
                                item.weightGrams(),
                                discounts.getOrDefault(id, BigDecimal.ZERO),
                                label,
                                snapshots.containsKey(id)
                                        ? mapper.writeValueAsString(snapshots.get(id))
                                        : null));
            }
            return List.copyOf(priced);
        } finally {
            MethodTiming.finish(
                    started,
                    MealPricing.class,
                    "apply(long,List<CreateOrderItemRequest>,List<ValidatedOrderItem>)");
        }
    }
}
