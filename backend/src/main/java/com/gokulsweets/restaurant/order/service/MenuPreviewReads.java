package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Shares only overlapping, identical public menu previews. No completed stock result is cached.
 * Cart checks and checkout continue to perform their own authoritative reads.
 */
@Service
public class MenuPreviewReads {
    private final CartAvailabilityService service;
    private final Clock clock;
    private final LongSupplier ticker;
    private final Map<Key, Pending> pending = new HashMap<>();

    /** Creates the request coordinator with a monotonic overlap clock. */
    @Autowired
    public MenuPreviewReads(CartAvailabilityService service, Clock inventoryClock) {
        this(service, inventoryClock, System::nanoTime);
    }

    /** Creates a coordinator with a controllable overlap clock for concurrency regressions. */
    MenuPreviewReads(CartAvailabilityService service, Clock clock, LongSupplier ticker) {
        this.service = service;
        this.clock = clock;
        this.ticker = ticker;
    }

    /** Request order, quantities, weights, branch and date are all part of the sharing identity. */
    private record Key(
            long branchId, LocalDate date, List<CreateOrderItemRequest> items, long second) {}

    /** One in-progress read; removed before its result or exception is published to subscribers. */
    private record Pending(
            long started, CompletableFuture<CartAvailabilityService.Availability> result) {}

    /**
     * Reads a one-date advisory preview, joining an identical read only during its first 250 ms.
     * Different requests run independently and saturation falls back to a fresh read. The registry
     * lock never covers database work or waiting. A new request after completion always reads
     * again.
     *
     * @param branchId selected branch
     * @param date selected pickup date
     * @param items complete ordered batch including requested quantities and weights
     * @return the advisory availability snapshot
     */
    public CartAvailabilityService.Availability check(
            long branchId, LocalDate date, List<CreateOrderItemRequest> items) {
        long started =
                MethodTiming.start(
                        MenuPreviewReads.class,
                        "check(long,LocalDate,List<CreateOrderItemRequest>)");
        try {
            var key = new Key(branchId, date, List.copyOf(items), clock.instant().getEpochSecond());
            Pending read;
            boolean owner = false;
            synchronized (pending) {
                read = pending.get(key);
                if (read != null
                        && ticker.getAsLong() - read.started()
                                > TimeUnit.MILLISECONDS.toNanos(
                                        AppConstant.MENU_PREVIEW_JOIN_MILLIS)) {
                    read = null;
                } else if (read == null && pending.size() < AppConstant.MENU_PREVIEW_MAX_PENDING) {
                    read = new Pending(ticker.getAsLong(), new CompletableFuture<>());
                    pending.put(key, read);
                    owner = true;
                }
            }
            if (read == null) return service.check(branchId, date, 1, items, true);
            if (!owner) {
                try {
                    return read.result().join();
                } catch (CompletionException failure) {
                    if (failure.getCause() instanceof RuntimeException original) throw original;
                    if (failure.getCause() instanceof Error original) throw original;
                    throw failure;
                }
            }
            try {
                var result = service.check(branchId, date, 1, items, true);
                synchronized (pending) {
                    pending.remove(key, read);
                }
                read.result().complete(result);
                return result;
            } catch (RuntimeException | Error failure) {
                synchronized (pending) {
                    pending.remove(key, read);
                }
                read.result().completeExceptionally(failure);
                throw failure;
            }
        } finally {
            MethodTiming.finish(
                    started,
                    MenuPreviewReads.class,
                    "check(long,LocalDate,List<CreateOrderItemRequest>)");
        }
    }
}
