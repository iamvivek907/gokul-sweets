package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.menu.dto.*;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Immutable, bounded snapshots. Only catalog data lives here; stock reservations never do. */
@Service
@RequiredArgsConstructor
public class MenuCatalogService {

    private final JdbcTemplate jdbc;

    private final Map<Long, Catalog> cache = new LinkedHashMap<>(16, .75f, true);

    /**
     * Immutable catalog data contract.
     *
     * @param revision the revision
     * @param categories the categories
     */
    public record Catalog(String revision, List<MenuCategoryResponse> categories) {}

    /**
     * Reads the database catalog-revision token used to validate cached branch catalogs.
     *
     * <p>Reads {@code menu_catalog_revision}.
     *
     * @return the {@code String} result
     */
    public String revision() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "revision()");
        try {
            return jdbc.queryForObject(
                    "SELECT token::text FROM menu_catalog_revision WHERE id=true", String.class);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuCatalogService.class, "revision()");
        }
    }

    /**
     * Requires branch.
     *
     * @param id the id
     */
    public void requireBranch(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "requireBranch(long)");
        try {
            var states =
                    jdbc.query(
                            "SELECT active,operational FROM branches WHERE id=?",
                            (r, n) -> new boolean[] {r.getBoolean(1), r.getBoolean(2)},
                            id);
            if (states.isEmpty())
                throw new IllegalArgumentException("Selected branch does not exist.");
            if (!states.getFirst()[0])
                throw new IllegalArgumentException("Selected branch is currently unavailable.");
            if (!states.getFirst()[1])
                throw new IllegalArgumentException("This branch is currently not operational.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuCatalogService.class, "requireBranch(long)");
        }
    }

    /**
     * Returns a branch catalog for a stable revision, rebuilding stale entries and bypassing the
     * shared cache outside read-only transactions.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Catalog} result
     * @throws org.springframework.web.server.ResponseStatusException when the method rejects the
     *     request with {@code Menu is updating. Please try again.}
     */
    @Transactional(readOnly = true)
    public Catalog get(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "get(long)");
        try {
            requireBranch(branchId);
            // Never publish uncommitted admin/import data into a shared process cache.
            if (!org.springframework.transaction.support.TransactionSynchronizationManager
                    .isCurrentTransactionReadOnly()) return build(branchId, revision());
            synchronized (cache) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    String revision = revision();
                    var existing = cache.get(branchId);
                    if (existing != null && existing.revision().equals(revision)) return existing;
                    var snapshot = build(branchId, revision);
                    // Do not publish a snapshot assembled across a committed catalog change.
                    if (!revision().equals(revision)) continue;
                    cache.put(branchId, snapshot);
                    while (cache.size() > 16
                            || cache.values().stream().mapToLong(this::estimatedBytes).sum()
                                    > 8 * 1024 * 1024)
                        cache.remove(cache.keySet().iterator().next());
                    return snapshot;
                }
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Menu is updating. Please try again.");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuCatalogService.class, "get(long)");
        }
    }

    /**
     * Estimateds bytes.
     *
     * @param value the value
     * @return the estimated bytes result
     */
    private long estimatedBytes(Catalog value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "estimatedBytes(Catalog)");
        try {
            return value.categories().stream()
                    .mapToLong(
                            c ->
                                    256
                                            + c.products().stream()
                                                    .mapToLong(
                                                            p ->
                                                                    512
                                                                            + 2L
                                                                                    * (length(
                                                                                                    p
                                                                                                            .name())
                                                                                            + length(
                                                                                                    p
                                                                                                            .description())
                                                                                            + length(
                                                                                                    p
                                                                                                            .imageUrl())
                                                                                            + length(
                                                                                                    p
                                                                                                            .categoryName())))
                                                    .sum())
                    .sum();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuCatalogService.class, "estimatedBytes(Catalog)");
        }
    }

    /**
     * Returns zero for a null string or its character count otherwise.
     *
     * @param value the value supplied to this method
     * @return the value of {@code value == null ? 0 : value.length()}
     */
    private int length(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "length(String)");
        try {
            return value == null ? 0 : value.length();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuCatalogService.class, "length(String)");
        }
    }

    /**
     * Loads a branch catalog snapshot from database rows and associates it with the supplied
     * revision token.
     *
     * <p>Reads {@code branch_products}, {@code categories}, {@code products}.
     *
     * @param branchId the branch id supplied to this method
     * @param revision the revision supplied to this method
     * @return the {@code Catalog} result
     */
    private Catalog build(long branchId, String revision) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuCatalogService.class, "build(long,String)");
        try {
            var categories = new LinkedHashMap<Long, MenuCategoryResponse>();
            var grouped = new LinkedHashMap<Long, List<MenuProductResponse>>();
            // Scalar JDBC reads bypass Hibernate's first-level cache on every retry.
            // The revision bracket rejects any concurrent committed edit during this query.
            jdbc.query(
                    """
SELECT c.id AS category_id,c.name AS category_name,c.description AS category_description,
       c.display_order AS category_order,p.id,p.name,p.description,
       COALESCE(bp.price_override,p.base_price) AS price,p.image_url,bp.available,
       p.sale_mode,p.minimum_weight_grams,p.weight_step_grams,p.vegetarian
FROM branch_products bp JOIN products p ON p.id=bp.product_id
JOIN categories c ON c.id=p.category_id
WHERE bp.branch_id=? AND p.active AND c.active AND NOT bp.occasion_only
ORDER BY c.display_order,bp.display_order,p.name
""",
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            r -> {
                                long id = r.getLong("category_id");
                                String name = r.getString("category_name");
                                categories.putIfAbsent(
                                        id,
                                        new MenuCategoryResponse(
                                                id,
                                                name,
                                                r.getString("category_description"),
                                                r.getInt("category_order"),
                                                List.of()));
                                grouped.computeIfAbsent(id, key -> new ArrayList<>())
                                        .add(
                                                new MenuProductResponse(
                                                        r.getLong("id"),
                                                        id,
                                                        name,
                                                        r.getString("name"),
                                                        r.getString("description"),
                                                        r.getBigDecimal("price"),
                                                        r.getString("image_url"),
                                                        r.getBoolean("available"),
                                                        ProductSaleMode.valueOf(
                                                                r.getString("sale_mode")),
                                                        r.getObject(
                                                                "minimum_weight_grams",
                                                                Integer.class),
                                                        r.getObject(
                                                                "weight_step_grams", Integer.class),
                                                        null,
                                                        r.getBoolean("vegetarian")));
                            },
                    branchId);
            return new Catalog(
                    revision,
                    categories.values().stream()
                            .map(
                                    c ->
                                            new MenuCategoryResponse(
                                                    c.id(),
                                                    c.name(),
                                                    c.description(),
                                                    c.displayOrder(),
                                                    List.copyOf(grouped.get(c.id()))))
                            .toList());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuCatalogService.class, "build(long,String)");
        }
    }
}
