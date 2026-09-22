package com.gokulsweets.restaurant.staff.controller;

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

@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
public class StaffManagementController {

    private final StaffManagementService
            staffManagementService;

    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> createStaff(
            @Valid @RequestBody
            CreateStaffRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        staffManagementService
                                .createStaff(request)
                );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<List<StaffResponse>> getStaff() {

        return ResponseEntity.ok(
                staffManagementService
                        .getAllStaff()
        );
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffManagementOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                staffManagementService
                        .getOptions()
        );
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> getStaff(
            @PathVariable Long staffId
    ) {

        return ResponseEntity.ok(
                staffManagementService
                        .getStaff(staffId)
        );
    }

    @PutMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<StaffResponse> updateStaff(
            @PathVariable Long staffId,
            @Valid @RequestBody
            UpdateStaffRequest request
    ) {

        return ResponseEntity.ok(
                staffManagementService
                        .updateStaff(
                                staffId,
                                request
                        )
        );
    }

    @PatchMapping("/{staffId}/password")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long staffId,
            @Valid @RequestBody
            ResetStaffPasswordRequest request
    ) {

        staffManagementService
                .resetPassword(
                        staffId,
                        request
                );

        return ResponseEntity
                .noContent()
                .build();
    }
}
