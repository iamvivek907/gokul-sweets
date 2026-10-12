package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchActiveRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchCreateRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchResponse;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchUpdateRequest;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for admin branch operations. */
@RestController
@RequestMapping("/api/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private final AdminBranchService adminBranchService;

    /*
     * =========================================================
     * LIST
     * =========================================================
     */
    /**
     * Returns branches.
     *
     * @return the get branches result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<List<AdminBranchResponse>> getBranches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchController.class, "getBranches()");
        try {
            return ResponseEntity.ok(adminBranchService.getBranches());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchController.class, "getBranches()");
        }
    }

    /*
     * =========================================================
     * DETAIL
     * =========================================================
     */
    /**
     * Returns branch.
     *
     * @param branchId the branch id
     * @return the get branch result
     */
    @GetMapping("/{branchId}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<AdminBranchResponse> getBranch(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchController.class, "getBranch(Long)");
        try {
            return ResponseEntity.ok(adminBranchService.getBranch(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchController.class, "getBranch(Long)");
        }
    }

    /*
     * =========================================================
     * CREATE
     * =========================================================
     */
    /**
     * Creates branch.
     *
     * @param request the request
     * @return the create branch result
     */
    @PostMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<AdminBranchResponse> createBranch(
            @Valid @RequestBody AdminBranchCreateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchController.class, "createBranch(AdminBranchCreateRequest)");
        try {
            AdminBranchResponse created = adminBranchService.createBranch(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchController.class,
                    "createBranch(AdminBranchCreateRequest)");
        }
    }

    /*
     * =========================================================
     * UPDATE
     * =========================================================
     */
    /**
     * Updates branch.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update branch result
     */
    @PutMapping("/{branchId}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<AdminBranchResponse> updateBranch(
            @PathVariable Long branchId, @Valid @RequestBody AdminBranchUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchController.class, "updateBranch(Long,AdminBranchUpdateRequest)");
        try {
            return ResponseEntity.ok(adminBranchService.updateBranch(branchId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchController.class,
                    "updateBranch(Long,AdminBranchUpdateRequest)");
        }
    }

    /*
     * =========================================================
     * ACTIVE STATUS
     * =========================================================
     */
    /**
     * Updates active status.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update active status result
     */
    @PatchMapping("/{branchId}/active")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<AdminBranchResponse> updateActiveStatus(
            @PathVariable Long branchId, @Valid @RequestBody AdminBranchActiveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchController.class,
                        "updateActiveStatus(Long,AdminBranchActiveRequest)");
        try {
            return ResponseEntity.ok(adminBranchService.updateActiveStatus(branchId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchController.class,
                    "updateActiveStatus(Long,AdminBranchActiveRequest)");
        }
    }
}
