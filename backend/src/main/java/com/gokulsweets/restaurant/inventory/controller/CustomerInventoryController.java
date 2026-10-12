package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckResponse;
import com.gokulsweets.restaurant.inventory.service.CustomerInventoryAvailabilityService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for customer inventory operations. */
@RestController
@RequestMapping("/api/branches/{branchId}/inventory")
@RequiredArgsConstructor
public class CustomerInventoryController {

    private final CustomerInventoryAvailabilityService availabilityService;

    /**
     * Handles {@code POST /api/branches/{branchId}/inventory/check} for customer inventory.
     *
     * <p>Delegates to {@code availabilityService.check(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.ok(availabilityService.check(branchId, request))}
     */
    @PostMapping("/check")
    public ResponseEntity<CustomerInventoryCheckResponse> check(
            @PathVariable Long branchId,
            @Valid @RequestBody CustomerInventoryCheckRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerInventoryController.class,
                        "check(Long,CustomerInventoryCheckRequest)");
        try {
            return ResponseEntity.ok(availabilityService.check(branchId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerInventoryController.class,
                    "check(Long,CustomerInventoryCheckRequest)");
        }
    }
}
