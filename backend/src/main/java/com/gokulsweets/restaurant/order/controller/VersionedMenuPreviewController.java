package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CompactMenuAvailability;
import com.gokulsweets.restaurant.order.service.MenuPreviewReads;
import com.gokulsweets.restaurant.order.service.VersionedMenuPreview;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Opt-in versioned advisory reads; legacy and authoritative endpoints retain their behaviour. */
@RestController
public class VersionedMenuPreviewController {
    private final EnhancementProperties features;
    private final MenuPreviewReads legacy;
    private final VersionedMenuPreview previews;
    private final boolean enabled;

    /** Keeps the optimisation independently reversible through a deployment setting. */
    public VersionedMenuPreviewController(
            EnhancementProperties features,
            MenuPreviewReads legacy,
            VersionedMenuPreview previews,
            @Value("${gokul.menu.versioned-previews-enabled:false}") boolean enabled) {
        this.features = features;
        this.legacy = legacy;
        this.previews = previews;
        this.enabled = enabled;
    }

    /** Reads an opted-in one-day preview; the flag-OFF path still performs fresh legacy checks. */
    @PostMapping(
            value = "/api/branches/{branchId}/availability",
            params = {"menuPreview=true", "compact=true", "versioned=true"})
    public ResponseEntity<?> check(
            @PathVariable long branchId,
            @Valid @RequestBody CartAvailabilityController.Request request) {
        long started =
                MethodTiming.start(
                        VersionedMenuPreviewController.class,
                        "check(long,CartAvailabilityController.Request)");
        try {
            if (!features.isSmartAvailability()) return ResponseEntity.notFound().build();
            if (request.days() != 1)
                throw new IllegalArgumentException("Menu previews require one date.");
            var result =
                    enabled
                            ? previews.check(branchId, request.startDate(), request.items())
                            : legacy.check(branchId, request.startDate(), request.items());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Gokul-Menu-Preview-Cache", enabled ? "enabled" : "disabled")
                    .body(CompactMenuAvailability.from(result));
        } finally {
            MethodTiming.finish(
                    started,
                    VersionedMenuPreviewController.class,
                    "check(long,CartAvailabilityController.Request)");
        }
    }

    /** Returns only a public change token; no customer, stock quantity or reservation data. */
    @GetMapping("/api/branches/{branchId}/menu-state")
    public ResponseEntity<State> state(@PathVariable long branchId, @RequestParam LocalDate date) {
        long started =
                MethodTiming.start(VersionedMenuPreviewController.class, "state(long,LocalDate)");
        try {
            if (!enabled || !features.isSmartAvailability())
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new State(previews.state(branchId, date)));
        } finally {
            MethodTiming.finish(
                    started, VersionedMenuPreviewController.class, "state(long,LocalDate)");
        }
    }

    /** Public invalidation identity. */
    public record State(String version) {}
}
