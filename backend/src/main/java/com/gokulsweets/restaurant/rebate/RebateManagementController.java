package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.rebate.dto.CreateRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.RebateResponse;
import com.gokulsweets.restaurant.rebate.dto.UpdateRebateRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for rebate management operations. */
@RestController
@RequestMapping("/api/admin/rebates")
@RequiredArgsConstructor
@Slf4j
public class RebateManagementController {

    private final RebateManagementService rebateManagementService;

    /**
     * Creates the operation.
     *
     * @param request the request
     * @return the create result
     */
    @PostMapping
    public ResponseEntity<RebateResponse> create(@Valid @RequestBody CreateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementController.class, "create(CreateRebateRequest)");
        try {
            log.debug(
                    "Admin rebate creation requested: code={}, scope={}, visibility={}, type={},"
                            + " branchId={}",
                    request.code(),
                    request.scope(),
                    request.visibility(),
                    request.rebateType(),
                    request.branchId());
            RebateResponse response = rebateManagementService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementController.class,
                    "create(CreateRebateRequest)");
        }
    }

    /**
     * Returns all.
     *
     * @return the get all result
     */
    @GetMapping
    public ResponseEntity<List<RebateResponse>> getAll() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementController.class, "getAll()");
        try {
            return ResponseEntity.ok(rebateManagementService.getAll());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementController.class, "getAll()");
        }
    }

    /**
     * Returns by id.
     *
     * @param rebateId the rebate id
     * @return the get by id result
     */
    @GetMapping("/{rebateId}")
    public ResponseEntity<RebateResponse> getById(@PathVariable Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementController.class, "getById(Long)");
        try {
            return ResponseEntity.ok(rebateManagementService.getById(rebateId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementController.class, "getById(Long)");
        }
    }

    /**
     * Updates the operation.
     *
     * @param rebateId the rebate id
     * @param request the request
     * @return the update result
     */
    @PutMapping("/{rebateId}")
    public ResponseEntity<RebateResponse> update(
            @PathVariable Long rebateId, @Valid @RequestBody UpdateRebateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateManagementController.class, "update(Long,UpdateRebateRequest)");
        try {
            return ResponseEntity.ok(rebateManagementService.update(rebateId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementController.class,
                    "update(Long,UpdateRebateRequest)");
        }
    }

    /**
     * Activates the operation.
     *
     * @param rebateId the rebate id
     * @return the activate result
     */
    @PatchMapping("/{rebateId}/activate")
    public ResponseEntity<RebateResponse> activate(@PathVariable Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementController.class, "activate(Long)");
        try {
            return ResponseEntity.ok(rebateManagementService.activate(rebateId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateManagementController.class, "activate(Long)");
        }
    }

    /**
     * Deactivates the operation.
     *
     * @param rebateId the rebate id
     * @return the deactivate result
     */
    @PatchMapping("/{rebateId}/deactivate")
    public ResponseEntity<RebateResponse> deactivate(@PathVariable Long rebateId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateManagementController.class, "deactivate(Long)");
        try {
            return ResponseEntity.ok(rebateManagementService.deactivate(rebateId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateManagementController.class,
                    "deactivate(Long)");
        }
    }
}
