package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.*;

/** Coordinates menu availability operations. */
@Service
@RequiredArgsConstructor
public class MenuAvailabilityService {

    private final MenuCatalogService catalog;

    private final MenuServiceWindows windows;

    private final Clock inventoryClock;

    private final Map<Long, Cached> cache = new LinkedHashMap<>(16, .75f, true);

    // Fixed stripes keep lock storage bounded; database work never holds the shared LRU lock.
    private final Object[] branchLocks =
            java.util.stream.IntStream.range(0, AppConstant.MENU_CACHE_LOCK_STRIPES)
                    .mapToObj(ignored -> new Object())
                    .toArray();

    /**
     * Immutable item data contract.
     *
     * @param productId the product id
     * @param available the available
     * @param serviceAvailability the service availability
     */
    public record Item(
            long productId, boolean available, MenuServiceWindows.Status serviceAvailability) {}

    /**
     * Immutable availability data contract.
     *
     * @param revision the revision
     * @param serviceWindowsEnabled the service windows enabled
     * @param items the items
     * @param observedAt the observed at
     */
    public record Availability(
            String revision, boolean serviceWindowsEnabled, List<Item> items, Instant observedAt) {

        /**
         * Creates a availability instance.
         *
         * @param revision the revision
         * @param serviceWindowsEnabled the service windows enabled
         * @param items the items
         */
        public Availability(String revision, boolean serviceWindowsEnabled, List<Item> items) {
            this(revision, serviceWindowsEnabled, items, null);
        }
    }

    /**
     * Immutable cached data contract.
     *
     * @param evaluatedAt the evaluated at
     * @param until the until
     * @param value the value
     */
    private record Cached(Instant evaluatedAt, Instant until, Availability value) {}

    /**
     * Returns get information for menu availability.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Availability} result
     * @throws org.springframework.web.server.ResponseStatusException when the method rejects the
     *     request with {@code Menu is updating. Please try again.}
     */
    @Transactional(readOnly = true)
    public Availability get(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAvailabilityService.class, "get(long)");
        try {
            // Menu flags are advisory. Never cache dated quantities, holds, or checkout acceptance.
            boolean publish = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            if (publish) {
                var observedCatalog = catalog.get(branchId);
                Instant observedNow = inventoryClock.instant();
                Cached observed;
                synchronized (cache) {
                    observed = cache.get(branchId);
                }
                if (observed != null
                        && observed.value().revision().equals(observedCatalog.revision())
                        && !observedNow.isBefore(observed.evaluatedAt())
                        && observedNow.isBefore(observed.until())) {
                    return new Availability(
                            observed.value().revision(),
                            observed.value().serviceWindowsEnabled(),
                            observed.value().items(),
                            observedNow);
                }
            }
            synchronized (branchLocks[Math.floorMod(branchId, branchLocks.length)]) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    var snapshot = catalog.get(branchId);
                    Instant now = inventoryClock.instant();
                    Cached cached;
                    synchronized (cache) {
                        cached = cache.get(branchId);
                    }
                    if (publish
                            && cached != null
                            && cached.value().revision().equals(snapshot.revision())
                            && !now.isBefore(cached.evaluatedAt())
                            && now.isBefore(cached.until()))
                        return new Availability(
                                cached.value().revision(),
                                cached.value().serviceWindowsEnabled(),
                                cached.value().items(),
                                now);
                    var live = windows.pickupSnapshot(branchId, null);
                    var items =
                            snapshot.categories().stream()
                                    .flatMap(c -> c.products().stream())
                                    .map(
                                            p -> {
                                                var status = live.status(p.id());
                                                return new Item(
                                                        p.id(),
                                                        p.available()
                                                                && (status == null
                                                                        || status.available()),
                                                        status);
                                            })
                                    .toList();
                    if (!catalog.revision().equals(snapshot.revision())) continue;
                    var result = new Availability(snapshot.revision(), live.enabled(), items, now);
                    if (publish) {
                        Instant until = now.plusSeconds(1);
                        for (var item : items)
                            if (item.serviceAvailability() != null
                                    && item.serviceAvailability().nextChangeAt() != null
                                    && item.serviceAvailability().nextChangeAt().isBefore(until))
                                until = item.serviceAvailability().nextChangeAt();
                        synchronized (cache) {
                            cache.put(branchId, new Cached(now, until, result));
                            while (cache.size() > 16
                                    || cache.values().stream()
                                                    .mapToLong(
                                                            c ->
                                                                    256L
                                                                            + 256L
                                                                                    * c.value()
                                                                                            .items()
                                                                                            .size())
                                                    .sum()
                                            > 4 * 1024 * 1024)
                                cache.remove(cache.keySet().iterator().next());
                        }
                    }
                    return result;
                }
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Menu is updating. Please try again.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAvailabilityService.class, "get(long)");
        }
    }
}
