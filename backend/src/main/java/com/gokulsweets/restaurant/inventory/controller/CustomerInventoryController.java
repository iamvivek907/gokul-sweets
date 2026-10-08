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
     * Checks the operation.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the check result
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
