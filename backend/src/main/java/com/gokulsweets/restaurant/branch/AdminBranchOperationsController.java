package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin branch operations operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/operational")
@RequiredArgsConstructor
public class AdminBranchOperationsController {

    private final BranchOperations operations;

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/operational} for admin branch operations.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code operations.get(branchId)}
     */
    @GetMapping
    public BranchOperations.Status get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchOperationsController.class, "get(long)");
        try {
            return operations.get(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchOperationsController.class, "get(long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/operational} for admin branch operations.
     *
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code operations.set(branchId, input)}
     */
    @PutMapping
    public BranchOperations.Status set(
            @PathVariable long branchId, @RequestBody BranchOperations.Status input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchOperationsController.class, "set(long,BranchOperations.Status)");
        try {
            return operations.set(branchId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchOperationsController.class,
                    "set(long,BranchOperations.Status)");
        }
    }
}
