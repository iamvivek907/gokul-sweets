package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.menu.dto.MenuProductResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** HTTP endpoints for storefront highlights operations. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StorefrontHighlightsController {

    private static final int MAX_CACHED_HIGHLIGHTS =
            AppConstant.STOREFRONT_HIGHLIGHTS_CONTROLLER_MAX_CACHED_HIGHLIGHTS;

    private static final int CACHE_TTL_SECONDS =
            AppConstant.STOREFRONT_HIGHLIGHTS_CONTROLLER_CACHE_TTL_SECONDS;

    private final EnhancementProperties features;

    private final MenuService menu;

    private final JdbcTemplate jdbc;

    private final CartAvailabilityService availability;

    private final Clock inventoryClock;

    private final Map<CacheKey, CacheEntry> highlightsCache = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Highlightses the operation.
     *
     * @param branchId the branch id
     * @return the highlights result
     */
    @GetMapping("/api/branches/{branchId}/storefront-highlights")
    public Highlights highlights(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StorefrontHighlightsController.class, "highlights(Long)");
        try {
            if (!features.isCustomerHomeV2() && !features.isContextualStorefrontV2())
                return new Highlights(List.of(), List.of(), List.of());
            LocalDate today = LocalDate.now(inventoryClock);
            CacheKey key = new CacheKey(branchId, today, features.getFutureOrderingDays());
            CacheEntry entry;
            synchronized (highlightsCache) {
                entry = highlightsCache.computeIfAbsent(key, ignored -> new CacheEntry());
                if (highlightsCache.size() > MAX_CACHED_HIGHLIGHTS) {
                    highlightsCache.remove(highlightsCache.keySet().iterator().next());
                }
            }
            // Only optional homepage hints are cached; cart and checkout still validate live
            // availability.
            synchronized (entry) {
                if (entry.highlights == null
                        || !inventoryClock.instant().isBefore(entry.expiresAt)) {
                    entry.highlights = loadHighlights(branchId, today);
                    entry.expiresAt = inventoryClock.instant().plusSeconds(CACHE_TTL_SECONDS);
                }
                return entry.highlights;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StorefrontHighlightsController.class,
                    "highlights(Long)");
        }
    }

    /**
     * Loads highlights.
     *
     * @param branchId the branch id
     * @param today the today
     * @return the load highlights result
     */
    private Highlights loadHighlights(Long branchId, LocalDate today) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StorefrontHighlightsController.class, "loadHighlights(Long,LocalDate)");
        try {
            Map<Long, MenuProductResponse> products =
                    menu.getMenu(branchId).stream()
                            .flatMap(category -> category.products().stream())
                            .filter(MenuProductResponse::available)
                            .collect(
                                    Collectors.toMap(MenuProductResponse::id, Function.identity()));
            // Rank by order count, not mixed piece/gram quantities. Reuse reporting aggregates,
            // never recalculate orders.
            List<Long> trending =
                    jdbc.queryForList(
                            """
                            SELECT product_id FROM analytics_product_daily
                            WHERE branch_id = ? AND business_date BETWEEN ? AND ?
                            GROUP BY product_id HAVING SUM(order_count) > 0
                            ORDER BY SUM(order_count) DESC, product_id ASC LIMIT 12
                            """,
                            Long.class,
                            branchId,
                            Date.valueOf(today.minusDays(29)),
                            Date.valueOf(today));
            List<Long> recent =
                    jdbc.queryForList(
                            """
SELECT p.id FROM products p JOIN branch_products bp ON bp.product_id = p.id
WHERE bp.branch_id = ? AND p.active = true AND bp.available = true
  AND p.created_at >= ?
ORDER BY p.created_at DESC, p.id DESC LIMIT 12
""",
                            Long.class,
                            branchId,
                            Date.valueOf(today.minusDays(30)));
            List<Long> latest =
                    jdbc.queryForList(
                            """
SELECT p.id FROM products p JOIN branch_products bp ON bp.product_id = p.id
WHERE bp.branch_id = ? AND p.active = true AND bp.available = true
ORDER BY p.created_at DESC, p.id DESC LIMIT 20
""",
                            Long.class,
                            branchId);
            Map<Long, Boolean> checked = new HashMap<>();
            return new Highlights(
                    availableIds(branchId, today, trending, products, checked),
                    availableIds(
                            branchId, today, recent,
                            products, // The latest-items display is a catalog preview; pickup
                            checked), // suitability is checked for the chosen date.
                    latest.stream().filter(products::containsKey).limit(8).toList());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StorefrontHighlightsController.class,
                    "loadHighlights(Long,LocalDate)");
        }
    }

    /**
     * Availables ids.
     *
     * @param branchId the branch id
     * @param today the today
     * @param ids the ids
     * @param products the products
     * @param checked the checked
     * @return the available ids result
     */
    private List<Long> availableIds(
            Long branchId,
            LocalDate today,
            List<Long> ids,
            Map<Long, MenuProductResponse> products,
            Map<Long, Boolean> checked) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StorefrontHighlightsController.class,
                        "availableIds(Long,LocalDate,List<Long>,Map<Long,MenuProductResponse>,Map<Long,Boolean>)");
        try {
            return ids.stream()
                    .filter(products::containsKey)
                    .filter(
                            id ->
                                    checked.computeIfAbsent(
                                            id,
                                            ignored -> {
                                                var product = products.get(id);
                                                boolean weighted =
                                                        product.saleMode()
                                                                == ProductSaleMode.WEIGHT;
                                                try {
                                                    return availability
                                                            .check(
                                                                    branchId,
                                                                    today,
                                                                    features.getFutureOrderingDays()
                                                                            + 1,
                                                                    List.of(
                                                                            new CreateOrderItemRequest(
                                                                                    id,
                                                                                    weighted
                                                                                            ? null
                                                                                            : 1,
                                                                                    weighted
                                                                                            ? product
                                                                                                    .minimumWeightGrams()
                                                                                            : null)))
                                                            .dates()
                                                            .stream()
                                                            .anyMatch(
                                                                    CartAvailabilityService
                                                                                    .DateAvailability
                                                                            ::available);
                                                } catch (IllegalArgumentException
                                                        | IllegalStateException exception) {
                                                    log.warn(
                                                            "Omitting unorderable storefront"
                                                                    + " highlight: branchId={},"
                                                                    + " productId={}, reason={}",
                                                            branchId,
                                                            id,
                                                            exception.getMessage());
                                                    return false;
                                                }
                                            }))
                    .limit(8)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StorefrontHighlightsController.class,
                    "availableIds(Long,LocalDate,List<Long>,Map<Long,MenuProductResponse>,Map<Long,Boolean>)");
        }
    }

    /** Immutable cache key data contract. */
    private record CacheKey(Long branchId, LocalDate date, int futureOrderingDays) {}

    /** Backend cache entry contract and implementation. */
    private static class CacheEntry {

        private Highlights highlights;

        private Instant expiresAt;
    }

    /** Immutable highlights data contract. */
    public record Highlights(
            List<Long> trendingProductIds, List<Long> newProductIds, List<Long> latestProductIds) {}
}
