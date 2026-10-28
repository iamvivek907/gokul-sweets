package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

/** Fixed-size anonymous histograms; no raw events, URLs or customer identifiers. */
@Service
public class StorefrontPerformance {
    private static final Set<String> METRICS =
            Set.of(
                    "TTFB",
                    "FCP",
                    "LCP",
                    "CLS",
                    "INP",
                    "FID",
                    "MENU_NETWORK",
                    "MENU_BODY",
                    "MENU_PREVIEW",
                    "MENU_RENDER",
                    "MENU_IMAGE",
                    "IMAGE_LOAD",
                    "SETTINGS_LOAD");
    private static final Set<String> PAGES =
            Set.of(
                    "home",
                    "menu",
                    "cart",
                    "checkout",
                    "branches",
                    "orders",
                    "profile",
                    "occasions",
                    "notifications",
                    "other");
    private static final double[] MILLIS = {
        50, 100, 200, 500, 1000, 1500, 2500, 4000, 8000, 15000, 30000, 60000, 120000
    };
    private static final double[] RATIOS = {
        0, .05, .1, .15, .25, .5, 1, 2, 5, 10, 100, 1000, 120000
    };
    private final Instant startedAt = Instant.now();

    /** Fixed coarse metric and route bucket. */
    private record Key(String name, String page) {}

    private final Map<Key, AtomicLongArray> histograms = new ConcurrentHashMap<>();

    /** Histogram upper bounds for one coarse page and metric. */
    public record Summary(
            String name,
            String page,
            long samples,
            String unit,
            double p75UpperBound,
            double p95UpperBound) {}

    /** Instance-scoped anonymous aggregate report. */
    public record Snapshot(Instant startedAt, String scope, List<Summary> metrics) {}

    /** Adds a validated measurement to a bounded histogram. */
    public void record(StorefrontVitalsController.Vital event) {
        long started =
                MethodTiming.start(
                        StorefrontPerformance.class, "record(StorefrontVitalsController.Vital)");
        try {
            if (event == null
                    || event.name() == null
                    || event.page() == null
                    || !METRICS.contains(event.name())
                    || !PAGES.contains(event.page())
                    || !Double.isFinite(event.value())
                    || event.value() < 0
                    || event.value() > 120000) return;
            var bounds = event.name().equals("CLS") ? RATIOS : MILLIS;
            int index = 0;
            while (index < bounds.length - 1 && event.value() > bounds[index]) index++;
            histograms
                    .computeIfAbsent(
                            new Key(event.name(), event.page()),
                            key -> new AtomicLongArray(bounds.length))
                    .incrementAndGet(index);
        } finally {
            MethodTiming.finish(
                    started,
                    StorefrontPerformance.class,
                    "record(StorefrontVitalsController.Vital)");
        }
    }

    /** Returns only aggregate bounds and counts, without raw measurements. */
    public Snapshot snapshot() {
        long started = MethodTiming.start(StorefrontPerformance.class, "snapshot()");
        try {
            var result = new ArrayList<Summary>();
            histograms.forEach(
                    (key, buckets) -> {
                        long[] counts = new long[buckets.length()];
                        long total = 0;
                        for (int i = 0; i < counts.length; i++) {
                            counts[i] = buckets.get(i);
                            total += counts[i];
                        }
                        if (total == 0) return;
                        var bounds = key.name().equals("CLS") ? RATIOS : MILLIS;
                        result.add(
                                new Summary(
                                        key.name(),
                                        key.page(),
                                        total,
                                        key.name().equals("CLS") ? "ratio" : "ms",
                                        percentile(counts, bounds, total, .75),
                                        percentile(counts, bounds, total, .95)));
                    });
            result.sort(Comparator.comparing(Summary::page).thenComparing(Summary::name));
            return new Snapshot(
                    startedAt,
                    "Current backend instance; sampled client documents",
                    List.copyOf(result));
        } finally {
            MethodTiming.finish(started, StorefrontPerformance.class, "snapshot()");
        }
    }

    /** Returns the upper bound containing the requested rank. */
    private static double percentile(long[] counts, double[] bounds, long total, double fraction) {
        long started =
                MethodTiming.start(
                        StorefrontPerformance.class, "percentile(long[],double[],long,double)");
        try {
            long rank = (long) Math.ceil(total * fraction), seen = 0;
            for (int i = 0; i < counts.length; i++) {
                seen += counts[i];
                if (seen >= rank) return bounds[i];
            }
            return bounds[bounds.length - 1];
        } finally {
            MethodTiming.finish(
                    started,
                    StorefrontPerformance.class,
                    "percentile(long[],double[],long,double)");
        }
    }
}
