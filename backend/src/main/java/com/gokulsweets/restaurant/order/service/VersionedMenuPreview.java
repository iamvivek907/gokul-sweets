package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded public preview cache; checkout and cart validation do not use this service. */
@Service
public class VersionedMenuPreview {
    private final MenuInventoryVersion versions;
    private final CartAvailabilityService service;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Map<Key, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private int retainedUnits;
    private final Map<StateKey, StateEntry> states = new LinkedHashMap<>(16, 0.75f, true);

    /** Bounded branch/date scope for lightweight live change hints. */
    private record StateKey(long branchId, LocalDate date) {}

    /** One-second hint reuse across customers on this replica; previews always probe afresh. */
    private record StateEntry(String version, Instant observedAt) {}

    /** Uses one repeatable-read snapshot for the source version and evaluated preview. */
    public VersionedMenuPreview(
            MenuInventoryVersion versions,
            CartAvailabilityService service,
            JdbcTemplate jdbc,
            Clock inventoryClock,
            PlatformTransactionManager manager) {
        this.versions = versions;
        this.service = service;
        this.jdbc = jdbc;
        this.clock = inventoryClock;
        transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(AppConstant.MENU_PREVIEW_TIMEOUT_SECONDS);
    }

    /**
     * Complete request identity; quantities, weights and order must never share another preview.
     */
    private record Key(long branchId, LocalDate date, List<CreateOrderItemRequest> items) {}

    /** A completed snapshot plus its clock validity and bounded retained item/issue count. */
    private record Entry(
            String version,
            Instant evaluatedAt,
            Instant expiresAt,
            CartAvailabilityService.Availability value,
            int units) {}

    /**
     * Checks a fresh database fingerprint before reusing a preview. Cache misses evaluate version
     * and stock in one snapshot, preventing a concurrent commit from relabelling stale stock.
     * Failed checks are not cached; database work never holds the cache monitor.
     */
    public CartAvailabilityService.Availability check(
            long branchId, LocalDate date, List<CreateOrderItemRequest> items) {
        long started =
                MethodTiming.start(
                        VersionedMenuPreview.class,
                        "check(long,LocalDate,List<CreateOrderItemRequest>)");
        try {
            Key key = new Key(branchId, date, List.copyOf(items));
            try {
                Entry evaluated =
                        transaction.execute(
                                status -> {
                                    jdbc.execute("SET LOCAL statement_timeout='5s'");
                                    String version = versions.read(branchId, date);
                                    Instant now = clock.instant();
                                    synchronized (cache) {
                                        Entry hit = cache.get(key);
                                        if (hit != null
                                                && hit.version().equals(version)
                                                && !now.isBefore(hit.evaluatedAt())
                                                && now.isBefore(hit.expiresAt())) {
                                            io.micrometer.core.instrument.Metrics.counter(
                                                            "gokul.menu.preview.lookups",
                                                            "outcome",
                                                            "hit")
                                                    .increment();
                                            return hit;
                                        }
                                    }
                                    io.micrometer.core.instrument.Metrics.counter(
                                                    "gokul.menu.preview.lookups", "outcome", "miss")
                                            .increment();
                                    Instant evaluatedAt = clock.instant();
                                    var result = service.check(branchId, date, 1, items, true);
                                    Instant expires = evaluatedAt.plusSeconds(300);
                                    if (result.recheckAt() == null) expires = evaluatedAt;
                                    if (result.recheckAt() != null
                                            && result.recheckAt().isBefore(expires))
                                        expires = result.recheckAt();
                                    int units =
                                            result.dates().stream()
                                                            .mapToInt(
                                                                    day ->
                                                                            day.items().size()
                                                                                    + day
                                                                                            .slots()
                                                                                            .stream()
                                                                                            .mapToInt(
                                                                                                    slot ->
                                                                                                            1
                                                                                                                    + slot.issues()
                                                                                                                            .size())
                                                                                            .sum())
                                                            .sum()
                                                    + items.size()
                                                    + 1;
                                    return new Entry(version, evaluatedAt, expires, result, units);
                                });
                // Publish only after the read transaction commits successfully.
                if (evaluated.units() <= 10000 && clock.instant().isBefore(evaluated.expiresAt())) {
                    synchronized (cache) {
                        Entry previous = cache.remove(key);
                        if (previous != null) retainedUnits -= previous.units();
                        while (!cache.isEmpty()
                                && (cache.size() >= 64
                                        || retainedUnits + evaluated.units() > 20000)) {
                            var iterator = cache.values().iterator();
                            retainedUnits -= iterator.next().units();
                            iterator.remove();
                        }
                        cache.put(key, evaluated);
                        retainedUnits += evaluated.units();
                    }
                }
                return evaluated.value();
            } catch (org.springframework.dao.TransientDataAccessException
                    | org.springframework.transaction.TransactionTimedOutException
                    | org.springframework.transaction.CannotCreateTransactionException failure) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Menu availability is temporarily busy. Please retry.",
                        failure);
            }
        } finally {
            MethodTiming.finish(
                    started,
                    VersionedMenuPreview.class,
                    "check(long,LocalDate,List<CreateOrderItemRequest>)");
        }
    }

    /**
     * Shares a short-lived public change hint across active browsers. This bounded cache has no
     * effect on checkout or preview validation, and never serves a token after a clock reversal.
     */
    public String state(long branchId, LocalDate date) {
        long started = MethodTiming.start(VersionedMenuPreview.class, "state(long,LocalDate)");
        try {
            var key = new StateKey(branchId, date);
            Instant now = clock.instant();
            synchronized (states) {
                var hit = states.get(key);
                if (hit != null
                        && !now.isBefore(hit.observedAt())
                        && now.isBefore(hit.observedAt().plusSeconds(1))) return hit.version();
            }
            String version =
                    transaction.execute(
                            status -> {
                                jdbc.execute("SET LOCAL statement_timeout='5s'");
                                return versions.read(branchId, date);
                            });
            synchronized (states) {
                while (states.size() >= 64) {
                    var iterator = states.keySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
                states.put(key, new StateEntry(version, now));
            }
            return version;
        } finally {
            MethodTiming.finish(started, VersionedMenuPreview.class, "state(long,LocalDate)");
        }
    }
}
