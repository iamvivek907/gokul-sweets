package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CartAvailabilityController {
    private final EnhancementProperties features;
    private final CartAvailabilityService service;
    private final com.gokulsweets.restaurant.menu.MenuPickupDiscoveryService discovery;

    @GetMapping("/api/branches/{branchId}/pickup-discovery")
    public ResponseEntity<CartAvailabilityService.Availability> discover(@PathVariable long branchId,@RequestParam LocalDate startDate,@RequestParam(defaultValue="31") int days) {
        if(!features.isSmartAvailability())return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(discovery.discover(branchId,startDate,days));
    }

    @PostMapping("/api/branches/{branchId}/availability")
    public ResponseEntity<CartAvailabilityService.Availability> check(@PathVariable Long branchId, @Valid @RequestBody Request request,
            @RequestParam(defaultValue = "false") boolean menuPreview) {
        if (!features.isSmartAvailability()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.check(branchId, request.startDate(), request.days(), request.items(), menuPreview));
    }

    // Omitted fulfilment retains legacy pickup semantics; delivery must not silently reuse pickup policies.
    public record Request(@NotNull LocalDate startDate, @Min(1) @Max(61) int days,
                          @NotEmpty @Size(max = 100) List<@NotNull @Valid CreateOrderItemRequest> items,
                          @Pattern(regexp = "PICKUP") String fulfilmentType) {}
}
