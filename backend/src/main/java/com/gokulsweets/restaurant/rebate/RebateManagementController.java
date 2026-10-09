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
     * Handles {@code POST /api/admin/rebates} for rebate management.
     *
     * <p>Delegates to {@code rebateManagementService.create(...)}.
     *
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.status(HttpStatus.CREATED).body(response)}
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
     * Handles {@code PUT /api/admin/rebates/{rebateId}} for rebate management.
     *
     * <p>Delegates to {@code rebateManagementService.update(...)}.
     *
     * @param rebateId the rebate id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code ResponseEntity.ok(rebateManagementService.update(rebateId,
     *     request))}
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
     * Handles {@code PATCH /api/admin/rebates/{rebateId}/activate} for rebate management.
     *
     * <p>Delegates to {@code rebateManagementService.activate(...)}.
     *
     * @param rebateId the rebate id supplied to this method
     * @return the value of {@code ResponseEntity.ok(rebateManagementService.activate(rebateId))}
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
     * Handles {@code PATCH /api/admin/rebates/{rebateId}/deactivate} for rebate management.
     *
     * <p>Delegates to {@code rebateManagementService.deactivate(...)}.
     *
     * @param rebateId the rebate id supplied to this method
     * @return the value of {@code ResponseEntity.ok(rebateManagementService.deactivate(rebateId))}
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
