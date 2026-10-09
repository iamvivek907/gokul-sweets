package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HTTP endpoints for cart availability operations. */
@RestController
@RequiredArgsConstructor
public class CartAvailabilityController {

    private final EnhancementProperties features;

    private final CartAvailabilityService service;

    private final com.gokulsweets.restaurant.menu.MenuPickupDiscoveryService discovery;

    private final com.gokulsweets.restaurant.order.service.MenuPreviewReads previewReads;

    /**
     * Handles {@code GET /api/branches/{branchId}/pickup-discovery} for cart availability.
     *
     * @param branchId the branch id supplied to this method
     * @param startDate the start date supplied to this method
     * @param days the days supplied to this method
     * @return the {@code ResponseEntity<CartAvailabilityService.Availability>} result
     */
    @GetMapping("/api/branches/{branchId}/pickup-discovery")
    public ResponseEntity<CartAvailabilityService.Availability> discover(
            @PathVariable long branchId,
            @RequestParam LocalDate startDate,
            @RequestParam(defaultValue = "31") int days) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CartAvailabilityController.class, "discover(long,LocalDate,int)");
        try {
            if (!features.isSmartAvailability()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(discovery.discover(branchId, startDate, days));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityController.class,
                    "discover(long,LocalDate,int)");
        }
    }

    /**
     * Handles {@code POST /api/branches/{branchId}/availability} for cart availability.
     *
     * <p>Delegates to {@code service.check(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param request the request supplied to this method
     * @param menuPreview the menu preview supplied to this method
     * @param compact whether to index repeated issues for an opt-in menu preview
     * @return the full availability or compact menu-preview transport
     */
    @PostMapping("/api/branches/{branchId}/availability")
    public ResponseEntity<?> check(
            @PathVariable Long branchId,
            @Valid @RequestBody Request request,
            @RequestParam(defaultValue = "false") boolean menuPreview,
            @RequestParam(defaultValue = "false") boolean compact) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CartAvailabilityController.class, "check(Long,Request,boolean,boolean)");
        try {
            if (!features.isSmartAvailability()) return ResponseEntity.notFound().build();
            var availability =
                    menuPreview && compact && request.days() == 1
                            ? previewReads.check(branchId, request.startDate(), request.items())
                            : service.check(
                                    branchId,
                                    request.startDate(),
                                    request.days(),
                                    request.items(),
                                    menuPreview);
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(
                            menuPreview && compact
                                    ? com.gokulsweets.restaurant.order.dto.CompactMenuAvailability
                                            .from(availability)
                                    : availability);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityController.class,
                    "check(Long,Request,boolean,boolean)");
        }
    }

    /**
     * Immutable request data contract.
     *
     * @param startDate the start date
     * @param days the days
     * @param items the items
     * @param fulfilmentType the fulfilment type
     */
    public record Request(
            @NotNull LocalDate startDate,
            @Min(1) @Max(61) int days,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid CreateOrderItemRequest> items,
            @Pattern(regexp = "PICKUP") String fulfilmentType) {}
}
