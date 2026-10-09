package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Backend menu service windows contract and implementation. */
@Service
@RequiredArgsConstructor
public class MenuServiceWindows {

    private final JdbcTemplate jdbc;

    private final Clock inventoryClock;

    private final StaffAuthorizationService authorization;

    /**
     * Immutable item data contract.
     *
     * @param branchProductId the branch product id
     * @param startsAt the starts at
     * @param endsAt the ends at
     * @param weekdays the weekdays
     * @param soldOut the sold out
     * @param requiresBranchProductId the requires branch product id
     */
    public record Item(
            long branchProductId,
            LocalTime startsAt,
            LocalTime endsAt,
            int weekdays,
            boolean soldOut,
            Long requiresBranchProductId) {}

    /**
     * Immutable settings data contract.
     *
     * @param enabled the enabled
     * @param revision the revision
     * @param items the items
     */
    public record Settings(boolean enabled, long revision, List<Item> items) {}

    /**
     * Immutable item hours data contract.
     *
     * @param enabled the enabled
     * @param revision the revision
     * @param item the item
     */
    public record ItemHours(boolean enabled, long revision, Item item) {}

    /**
     * Immutable hours edit data contract.
     *
     * @param revision the revision
     * @param startsAt the starts at
     * @param endsAt the ends at
     */
    public record HoursEdit(
            @jakarta.validation.constraints.Min(0) long revision,
            LocalTime startsAt,
            LocalTime endsAt) {}

    /**
     * Immutable status data contract.
     *
     * @param available the available
     * @param code the code
     * @param message the message
     * @param nextChangeAt the next change at
     * @param evaluatedAt the evaluated at
     */
    public record Status(
            boolean available,
            String code,
            String message,
            Instant nextChangeAt,
            Instant evaluatedAt) {

        /**
         * Creates a status instance.
         *
         * @param available the available
         * @param code the code
         * @param message the message
         * @param nextChangeAt the next change at
         */
        public Status(boolean available, String code, String message, Instant nextChangeAt) {
            this(available, code, message, nextChangeAt, null);
        }
    }

    /**
     * Immutable product state data contract.
     *
     * @param id the id
     * @param available the available
     * @param name the name
     */
    private record ProductState(long id, boolean available, String name) {}

    /**
     * Immutable snapshot data contract.
     *
     * @param enabled the enabled
     * @param products the products
     */
    public record Snapshot(boolean enabled, Map<Long, Status> products) {

        /**
         * Returns status information for snapshot.
         *
         * @param productId the product id supplied to this method
         * @return the value of {@code products.get(productId)}
         */
        public Status status(long productId) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuServiceWindows.Snapshot.class, "status(long)");
            try {
                return products.get(productId);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuServiceWindows.Snapshot.class,
                        "status(long)");
            }
        }

        /**
         * Requires available.
         *
         * @param productId the product id
         */
        public void requireAvailable(long productId) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuServiceWindows.Snapshot.class, "requireAvailable(long)");
            try {
                var status = status(productId);
                if (enabled && status != null && !status.available())
                    throw new IllegalArgumentException(status.message());
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuServiceWindows.Snapshot.class,
                        "requireAvailable(long)");
            }
        }
    }

    /**
     * Requires menu-management and branch access before returning the branch service-window policy.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code readSettings(branchId)}
     */
    @Transactional(readOnly = true)
    public Settings settings(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "settings(long)");
        try {
            authorize(branchId);
            return readSettings(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "settings(long)");
        }
    }

    /**
     * Requires menu-management permission, branch access and an existing branch.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * <p>Reads {@code branches}.
     *
     * @param branchId the branch id supplied to this method
     * @throws IllegalArgumentException when the method rejects the request with {@code Branch not
     *     found.}
     */
    private void authorize(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branches WHERE id=?)",
                            Boolean.class,
                            branchId))) throw new IllegalArgumentException("Branch not found.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "authorize(long)");
        }
    }

    /**
     * Returns the branch item's configured service hours after checking menu-management and branch
     * access.
     *
     * @param branchId the branch id supplied to this method
     * @param branchProductId the branch product id supplied to this method
     * @return the value of {@code readHours(branchId, branchProductId)}
     */
    @Transactional(readOnly = true)
    public ItemHours hours(long branchId, long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "hours(long,long)");
        try {
            authorize(branchId);
            return readHours(branchId, branchProductId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "hours(long,long)");
        }
    }

    /**
     * Reads hours.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @return the read hours result
     */
    private ItemHours readHours(long branchId, long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "readHours(long,long)");
        try {
            // One statement gives the rule and its optimistic revision from the same snapshot.
            var rows =
                    jdbc.query(
                            "SELECT COALESCE(p.enabled,false) enabled,COALESCE(p.revision,0)"
                                + " revision,s.starts_at,s.ends_at,COALESCE(s.weekdays,127)"
                                + " weekdays,COALESCE(s.sold_out,false)"
                                + " sold_out,s.requires_branch_product_id FROM branch_products b"
                                + " LEFT JOIN menu_service_policies p ON p.branch_id=b.branch_id"
                                + " LEFT JOIN menu_service_items s ON s.branch_product_id=b.id"
                                + " WHERE b.branch_id=? AND b.id=?",
                            (r, n) ->
                                    new ItemHours(
                                            r.getBoolean("enabled"),
                                            r.getLong("revision"),
                                            new Item(
                                                    branchProductId,
                                                    r.getObject("starts_at", LocalTime.class),
                                                    r.getObject("ends_at", LocalTime.class),
                                                    r.getInt("weekdays"),
                                                    r.getBoolean("sold_out"),
                                                    (Long)
                                                            r.getObject(
                                                                    "requires_branch_product_id"))),
                            branchId,
                            branchProductId);
            if (rows.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not assigned to this branch.");
            return rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "readHours(long,long)");
        }
    }

    /**
     * Saves hours.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param input the input
     * @return the save hours result
     */
    @Transactional
    public ItemHours saveHours(long branchId, long branchProductId, HoursEdit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "saveHours(long,long,HoursEdit)");
        try {
            authorize(branchId);
            if (input == null
                    || input.revision() < 0
                    || (input.startsAt() == null) != (input.endsAt() == null)
                    || input.startsAt() != null && input.startsAt().equals(input.endsAt()))
                throw new IllegalArgumentException(
                        "Enter both service times with different start and end times, or clear"
                                + " both.");
            // Serialize with branch-wide rules, inventory batches and order acceptance.
            jdbc.queryForObject(
                    "SELECT id FROM branches WHERE id=? FOR UPDATE", Long.class, branchId);
            var assigned =
                    jdbc.queryForList(
                            "SELECT id FROM branch_products WHERE branch_id=? AND id=? FOR UPDATE",
                            Long.class,
                            branchId,
                            branchProductId);
            if (assigned.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not assigned to this branch.");
            var current = readHours(branchId, branchProductId);
            if (current.revision() != input.revision())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Service rules changed. Reload saved hours before applying your edit.");
            jdbc.update(
                    "INSERT INTO"
                        + " menu_service_items(branch_product_id,starts_at,ends_at,weekdays,sold_out,requires_branch_product_id)"
                        + " VALUES (?,?,?,127,false,NULL) ON CONFLICT(branch_product_id) DO UPDATE"
                        + " SET starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at",
                    branchProductId,
                    input.startsAt(),
                    input.endsAt());
            // An individual item edit must never enable all saved rules for the branch implicitly.
            jdbc.update(
                    "INSERT INTO menu_service_policies(branch_id,enabled,revision) VALUES"
                            + " (?,false,1) ON CONFLICT(branch_id) DO UPDATE SET"
                            + " revision=menu_service_policies.revision+1",
                    branchId);
            return readHours(branchId, branchProductId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "saveHours(long,long,HoursEdit)");
        }
    }

    /**
     * Reads settings.
     *
     * @param branchId the branch id
     * @return the read settings result
     */
    private Settings readSettings(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "readSettings(long)");
        try {
            return readSettings(branchId, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "readSettings(long)");
        }
    }

    /**
     * Reads settings.
     *
     * @param branchId the branch id
     * @param includeDisabledItems the include disabled items
     * @return the read settings result
     */
    private Settings readSettings(long branchId, boolean includeDisabledItems) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "readSettings(long,boolean)");
        try {
            var policy =
                    jdbc.query(
                            "SELECT enabled,revision FROM menu_service_policies WHERE branch_id=?",
                            (r, n) -> new Settings(r.getBoolean(1), r.getLong(2), List.of()),
                            branchId);
            if (!includeDisabledItems && (policy.isEmpty() || !policy.getFirst().enabled()))
                return new Settings(
                        false, policy.isEmpty() ? 0 : policy.getFirst().revision(), List.of());
            var items =
                    jdbc.query(
                            "SELECT s.* FROM menu_service_items s JOIN branch_products b ON"
                                    + " b.id=s.branch_product_id WHERE b.branch_id=? ORDER BY"
                                    + " s.branch_product_id",
                            (r, n) ->
                                    new Item(
                                            r.getLong("branch_product_id"),
                                            r.getObject("starts_at", LocalTime.class),
                                            r.getObject("ends_at", LocalTime.class),
                                            r.getInt("weekdays"),
                                            r.getBoolean("sold_out"),
                                            (Long) r.getObject("requires_branch_product_id")),
                            branchId);
            return new Settings(
                    !policy.isEmpty() && policy.getFirst().enabled(),
                    policy.isEmpty() ? 0 : policy.getFirst().revision(),
                    items);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "readSettings(long,boolean)");
        }
    }

    /**
     * Replaces a branch's item rules under the branch lock, rejecting stale revisions, invalid
     * schedules and cyclic ingredient dependencies.
     *
     * <p>Reads {@code branch_products}, {@code branches}.
     *
     * <p>Writes {@code menu_service_items}, {@code menu_service_policies}.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code readSettings(branchId)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose valid
     *     menu rules.}; {@code Ingredient dependencies must not form a loop.}; {@code Use at most
     *     ten linked ingredient items.}; {@code Use unique branch items, valid days and different
     *     opening/closing times.}
     * @throws ResponseStatusException when the method rejects the request with {@code Menu timing
     *     changed. Reload before saving.}
     */
    @Transactional
    public Settings save(long branchId, Settings input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "save(long,Settings)");
        try {
            authorize(branchId);
            // Same parent lock used by order acceptance, including branches with no saved policy
            // yet.
            jdbc.queryForObject(
                    "SELECT id FROM branches WHERE id=? FOR UPDATE", Long.class, branchId);
            var current = readSettings(branchId);
            if (input == null || input.items() == null || input.items().size() > 5000)
                throw new IllegalArgumentException("Choose valid menu rules.");
            if (input.revision() != current.revision())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Menu timing changed. Reload before saving.");
            var ids =
                    new HashSet<>(
                            jdbc.queryForList(
                                    "SELECT id FROM branch_products WHERE branch_id=?",
                                    Long.class,
                                    branchId));
            var byId = new HashMap<Long, Item>();
            for (var item : input.items()) {
                if (item == null
                        || !ids.contains(item.branchProductId())
                        || byId.put(item.branchProductId(), item) != null
                        || item.weekdays() < 1
                        || item.weekdays() > 127
                        || (item.startsAt() == null) != (item.endsAt() == null)
                        || item.startsAt() != null && item.startsAt().equals(item.endsAt())
                        || item.requiresBranchProductId() != null
                                && !ids.contains(item.requiresBranchProductId()))
                    throw new IllegalArgumentException(
                            "Use unique branch items, valid days and different opening/closing"
                                    + " times.");
            }
            for (var item : input.items()) {
                var visited = new HashSet<Long>();
                Long next = item.branchProductId();
                while (next != null) {
                    if (visited.size() >= 10)
                        throw new IllegalArgumentException(
                                "Use at most ten linked ingredient items.");
                    if (!visited.add(next))
                        throw new IllegalArgumentException(
                                "Ingredient dependencies must not form a loop.");
                    var rule = byId.get(next);
                    next = rule == null ? null : rule.requiresBranchProductId();
                }
            }
            jdbc.update(
                    "DELETE FROM menu_service_items WHERE branch_product_id IN (SELECT id FROM"
                            + " branch_products WHERE branch_id=?)",
                    branchId);
            for (var item : input.items())
                jdbc.update(
                        "INSERT INTO"
                            + " menu_service_items(branch_product_id,starts_at,ends_at,weekdays,sold_out,requires_branch_product_id)"
                            + " VALUES (?,?,?,?,?,?)",
                        item.branchProductId(),
                        item.startsAt(),
                        item.endsAt(),
                        item.weekdays(),
                        item.soldOut(),
                        item.requiresBranchProductId());
            jdbc.update(
                    "INSERT INTO menu_service_policies(branch_id,enabled,revision) VALUES (?,?,1)"
                            + " ON CONFLICT(branch_id) DO UPDATE SET"
                            + " enabled=EXCLUDED.enabled,revision=menu_service_policies.revision+1",
                    branchId,
                    input.enabled());
            return readSettings(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "save(long,Settings)");
        }
    }

    /**
     * Evaluates the branch's service-window rules at the current business time in Asia/Kolkata.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Snapshot} result
     */
    public Snapshot snapshot(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "snapshot(long)");
        try {
            return pickupEvaluator(branchId)
                    .apply(
                            LocalDateTime.ofInstant(
                                    inventoryClock.instant(), ZoneId.of("Asia/Kolkata")));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "snapshot(long)");
        }
    }

    /**
     * No pickup selected: retain manual/sold-out rules without blocking advance browsing.
     *
     * @param branchId the branch id
     * @param pickupAt the pickup at
     * @return the operation result
     */
    public Snapshot pickupSnapshot(long branchId, LocalDateTime pickupAt) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "pickupSnapshot(long,LocalDateTime)");
        try {
            return pickupEvaluator(branchId).apply(pickupAt);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "pickupSnapshot(long,LocalDateTime)");
        }
    }

    /**
     * Pickups snapshot.
     *
     * @param branchId the branch id
     * @param pickupAt the pickup at
     * @param productIds the product ids
     * @return the pickup snapshot result
     */
    public Snapshot pickupSnapshot(long branchId, LocalDateTime pickupAt, Set<Long> productIds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuServiceWindows.class, "pickupSnapshot(long,LocalDateTime,Set<Long>)");
        try {
            return pickupEvaluator(branchId, productIds).apply(pickupAt);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "pickupSnapshot(long,LocalDateTime,Set<Long>)");
        }
    }

    /**
     * Read rules once for an entire calendar; each slot uses its own server-owned IST timestamp.
     *
     * @param branchId the branch id
     * @return the operation result
     */
    public java.util.function.Function<LocalDateTime, Snapshot> pickupEvaluator(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "pickupEvaluator(long)");
        try {
            return pickupEvaluator(branchId, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuServiceWindows.class, "pickupEvaluator(long)");
        }
    }

    /**
     * Evaluate only requested SKUs; dependencies may still reference any item in the branch.
     *
     * @param branchId the branch id
     * @param productIds the product ids
     * @return the operation result
     */
    public java.util.function.Function<LocalDateTime, Snapshot> pickupEvaluator(
            long branchId, Set<Long> productIds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuServiceWindows.class, "pickupEvaluator(long,Set<Long>)");
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                jdbc.queryForObject(
                        "SELECT id FROM branches WHERE id=? FOR SHARE", Long.class, branchId);
            Boolean operational =
                    jdbc.queryForObject(
                            "SELECT operational FROM branches WHERE id=?", Boolean.class, branchId);
            if (!Boolean.TRUE.equals(operational))
                throw new IllegalArgumentException("This branch is currently not operational.");
            var settings = readSettings(branchId, false);
            if (!settings.enabled()) return ignored -> new Snapshot(false, Map.of());
            var states =
                    jdbc.query(
                            "SELECT b.id,b.product_id,b.available AND p.active AND c.active"
                                + " available,p.name FROM branch_products b JOIN products p ON"
                                + " p.id=b.product_id JOIN categories c ON c.id=p.category_id WHERE"
                                + " b.branch_id=?",
                            (r, n) ->
                                    Map.entry(
                                            r.getLong("product_id"),
                                            new ProductState(
                                                    r.getLong("id"),
                                                    r.getBoolean("available"),
                                                    r.getString("name"))),
                            branchId);
            var products = new HashMap<Long, ProductState>();
            states.forEach(e -> products.put(e.getKey(), e.getValue()));
            var byBranchProduct = new HashMap<Long, ProductState>();
            products.values().forEach(p -> byBranchProduct.put(p.id(), p));
            var rules = new HashMap<Long, Item>();
            settings.items().forEach(i -> rules.put(i.branchProductId(), i));
            var requestedIds =
                    productIds == null ? Set.copyOf(products.keySet()) : Set.copyOf(productIds);
            return pickupAt -> {
                var statuses = new HashMap<Long, Status>();
                var at = pickupAt == null ? null : pickupAt.atZone(ZoneId.of("Asia/Kolkata"));
                var observedAt = inventoryClock.instant();
                var windowEvaluations = new WindowEvaluations(at);
                requestedIds.forEach(
                        id -> {
                            var p = products.get(id);
                            var state =
                                    p == null
                                            ? new Status(
                                                    false,
                                                    "DEPENDENCY_UNAVAILABLE",
                                                    "Required item is unavailable.",
                                                    null)
                                            : evaluate(
                                                    p.id(),
                                                    byBranchProduct,
                                                    rules,
                                                    at,
                                                    new HashSet<>(),
                                                    windowEvaluations);
                            statuses.put(
                                    id,
                                    new Status(
                                            state.available(),
                                            state.code(),
                                            state.message(),
                                            state.nextChangeAt(),
                                            observedAt));
                        });
                return new Snapshot(true, Map.copyOf(statuses));
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "pickupEvaluator(long,Set<Long>)");
        }
    }

    /**
     * Evaluates item availability, service hours and ingredient dependencies, returning the reason
     * and next relevant change time.
     *
     * @param id the id supplied to this method
     * @param products the products supplied to this method
     * @param rules the rules supplied to this method
     * @param now the now supplied to this method
     * @param visited the visited supplied to this method
     * @return the {@code Status} result
     */
    private Status evaluate(
            long id,
            Map<Long, ProductState> products,
            Map<Long, Item> rules,
            ZonedDateTime now,
            Set<Long> visited,
            WindowEvaluations windowEvaluations) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuServiceWindows.class,
                        "evaluate(long,Map<Long,ProductState>,Map<Long,Item>,ZonedDateTime,Set<Long>,WindowEvaluations)");
        try {
            var product = products.get(id);
            var rule = rules.get(id);
            if (visited.size() >= 10 || !visited.add(id) || product == null)
                return new Status(
                        false, "DEPENDENCY_UNAVAILABLE", "Required item is unavailable.", null);
            if (!product.available() || rule != null && rule.soldOut())
                return new Status(false, "SOLD_OUT", product.name() + " is sold out.", null);
            Instant next = null;
            if (rule != null) {
                var window = new ServiceWindow(rule.startsAt(), rule.endsAt(), rule.weekdays());
                var evaluatedWindow = windowEvaluations.get(window);
                next = evaluatedWindow.nextChange();
                Status dependency =
                        rule.requiresBranchProductId() == null
                                ? null
                                : evaluate(
                                        rule.requiresBranchProductId(),
                                        products,
                                        rules,
                                        now,
                                        visited,
                                        windowEvaluations);
                if (dependency != null && dependency.code().equals("SOLD_OUT"))
                    return new Status(
                            false,
                            "SOLD_OUT",
                            product.name() + " is unavailable: " + dependency.message(),
                            null);
                if (now != null && !evaluatedWindow.active()) {
                    String time =
                            next == null
                                    ? "later"
                                    : DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.ENGLISH)
                                                    .format(next.atZone(now.getZone()))
                                            + " IST";
                    return new Status(
                            false,
                            "OUTSIDE_SERVICE",
                            product.name()
                                    + " is unavailable for this pickup time. Choose pickup from "
                                    + time
                                    + ".",
                            next);
                }
                if (dependency != null) {
                    if (!dependency.available())
                        return new Status(
                                false,
                                dependency.code().equals("SOLD_OUT")
                                        ? "SOLD_OUT"
                                        : "DEPENDENCY_UNAVAILABLE",
                                product.name() + " is unavailable: " + dependency.message(),
                                dependency.nextChangeAt());
                    if (dependency.nextChangeAt() != null
                            && (next == null || dependency.nextChangeAt().isBefore(next)))
                        next = dependency.nextChangeAt();
                }
            }
            return new Status(true, "AVAILABLE", null, next);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuServiceWindows.class,
                    "evaluate(long,Map<Long,ProductState>,Map<Long,Item>,ZonedDateTime,Set<Long>,WindowEvaluations)");
        }
    }
}
