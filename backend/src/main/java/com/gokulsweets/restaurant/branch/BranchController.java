package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for branch operations. */
@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /**
     * Returns active branches.
     *
     * @return the get active branches result
     */
    @GetMapping
    public List<BranchResponse> getActiveBranches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchController.class, "getActiveBranches()");
        try {
            return branchService.getActiveBranches();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchController.class, "getActiveBranches()");
        }
    }

    /**
     * Returns branch.
     *
     * @param id the id
     * @return the get branch result
     */
    @GetMapping("/{id}")
    public BranchResponse getBranch(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchController.class, "getBranch(Long)");
        try {
            return branchService.getBranch(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchController.class, "getBranch(Long)");
        }
    }
}
