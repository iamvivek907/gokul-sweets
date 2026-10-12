package com.gokulsweets.restaurant.staff.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffManagementService;
import com.gokulsweets.restaurant.staff.dto.CreateStaffRequest;
import com.gokulsweets.restaurant.staff.dto.ResetStaffPasswordRequest;
import com.gokulsweets.restaurant.staff.dto.StaffManagementOptionsResponse;
import com.gokulsweets.restaurant.staff.dto.StaffResponse;
import com.gokulsweets.restaurant.staff.dto.UpdateStaffRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for staff management operations. */
@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
public class StaffManagementController {

    private final StaffManagementService staffManagementService;

    /**
     * Creates staff.
     *
     * @param request the request
     * @return the create staff result
     */
    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> createStaff(
            @Valid @RequestBody CreateStaffRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementController.class, "createStaff(CreateStaffRequest)");
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(staffManagementService.createStaff(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementController.class,
                    "createStaff(CreateStaffRequest)");
        }
    }

    /**
     * Returns staff.
     *
     * @return the get staff result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<List<StaffResponse>> getStaff() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementController.class, "getStaff()");
        try {
            return ResponseEntity.ok(staffManagementService.getAllStaff());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementController.class, "getStaff()");
        }
    }

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffManagementOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementController.class, "getOptions()");
        try {
            return ResponseEntity.ok(staffManagementService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementController.class, "getOptions()");
        }
    }

    /**
     * Returns staff.
     *
     * @param staffId the staff id
     * @return the get staff result
     */
    @GetMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> getStaff(@PathVariable Long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffManagementController.class, "getStaff(Long)");
        try {
            return ResponseEntity.ok(staffManagementService.getStaff(staffId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffManagementController.class, "getStaff(Long)");
        }
    }

    /**
     * Updates staff.
     *
     * @param staffId the staff id
     * @param request the request
     * @return the update staff result
     */
    @PutMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> updateStaff(
            @PathVariable Long staffId, @Valid @RequestBody UpdateStaffRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementController.class, "updateStaff(Long,UpdateStaffRequest)");
        try {
            return ResponseEntity.ok(staffManagementService.updateStaff(staffId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementController.class,
                    "updateStaff(Long,UpdateStaffRequest)");
        }
    }

    /**
     * Resets password.
     *
     * @param staffId the staff id
     * @param request the request
     * @return the reset password result
     */
    @PatchMapping("/{staffId}/password")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long staffId, @Valid @RequestBody ResetStaffPasswordRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffManagementController.class,
                        "resetPassword(Long,ResetStaffPasswordRequest)");
        try {
            staffManagementService.resetPassword(staffId, request);
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffManagementController.class,
                    "resetPassword(Long,ResetStaffPasswordRequest)");
        }
    }
}
