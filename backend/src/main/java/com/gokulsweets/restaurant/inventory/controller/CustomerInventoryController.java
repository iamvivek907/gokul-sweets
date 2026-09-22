package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckResponse;
import com.gokulsweets.restaurant.inventory.service.CustomerInventoryAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branches/{branchId}/inventory")
@RequiredArgsConstructor
public class CustomerInventoryController {

    private final CustomerInventoryAvailabilityService availabilityService;

    @PostMapping("/check")
    public ResponseEntity<CustomerInventoryCheckResponse> check(
            @PathVariable Long branchId,
            @Valid @RequestBody CustomerInventoryCheckRequest request
    ) {
        return ResponseEntity.ok(availabilityService.check(branchId, request));
    }
}
