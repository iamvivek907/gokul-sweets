package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Anonymous sampled browser timings; log only a coarse page bucket and validated metric. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StorefrontVitalsController {

    private static final Set<String> METRICS = Set.of("TTFB", "FCP", "LCP", "CLS", "INP", "FID");

    private static final Set<String> RATINGS = Set.of("good", "needs-improvement", "poor");

    private static final Set<String> PAGES = Set.of("home", "menu", "cart", "checkout", "other");

    private final EnhancementProperties features;

    /**
     * Handles {@code POST /api/storefront/vitals} for storefront vitals.
     *
     * @param metric the metric supplied to this method
     * @return the {@code ResponseEntity<Void>} result
     */
    @PostMapping("/api/storefront/vitals")
    public ResponseEntity<Void> receive(@RequestBody Vital metric) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StorefrontVitalsController.class, "receive(Vital)");
        try {
            if (!features.isAccessibleOrderingV2()) return ResponseEntity.noContent().build();
            if (metric == null
                    || !METRICS.contains(metric.name())
                    || !RATINGS.contains(metric.rating())
                    || !PAGES.contains(metric.page())
                    || metric.value() < 0
                    || metric.value() > 120_000
                    || !Double.isFinite(metric.value())) return ResponseEntity.badRequest().build();
            log.info(
                    "storefront_vital name={} page={} rating={} value={}",
                    metric.name(),
                    metric.page(),
                    metric.rating(),
                    metric.value());
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StorefrontVitalsController.class, "receive(Vital)");
        }
    }

    /**
     * Immutable vital data contract.
     *
     * @param name the name
     * @param value the value
     * @param rating the rating
     * @param page the page
     */
    public record Vital(String name, double value, String rating, String page) {}
}
