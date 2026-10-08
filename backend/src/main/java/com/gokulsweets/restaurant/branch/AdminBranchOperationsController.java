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
     * Returns the operation.
     *
     * @param branchId the branch id
     * @return the get result
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
     * Updates the operation.
     *
     * @param branchId the branch id
     * @param input the input
     * @return the set result
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
