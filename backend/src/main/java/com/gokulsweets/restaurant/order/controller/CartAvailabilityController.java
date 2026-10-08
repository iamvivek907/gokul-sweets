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

    /**
     * Discovers the operation.
     *
     * @param branchId the branch id
     * @param startDate the start date
     * @param days the days
     * @return the discover result
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
     * Checks the operation.
     *
     * @param branchId the branch id
     * @param request the request
     * @param menuPreview the menu preview
     * @return the check result
     */
    @PostMapping("/api/branches/{branchId}/availability")
    public ResponseEntity<CartAvailabilityService.Availability> check(
            @PathVariable Long branchId,
            @Valid @RequestBody Request request,
            @RequestParam(defaultValue = "false") boolean menuPreview) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CartAvailabilityController.class, "check(Long,Request,boolean)");
        try {
            if (!features.isSmartAvailability()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(
                    service.check(
                            branchId,
                            request.startDate(),
                            request.days(),
                            request.items(),
                            menuPreview));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CartAvailabilityController.class,
                    "check(Long,Request,boolean)");
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
