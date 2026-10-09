package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for branch discovery operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches/{branchId}/discovery")
public class BranchDiscoveryController {

    private final BranchDiscoveryService discovery;

    /**
     * Handles {@code GET /api/branches/{branchId}/discovery} for branch discovery.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code discovery.get(branchId)}
     */
    @GetMapping
    public BranchDiscoveryService.Discovery get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchDiscoveryController.class, "get(long)");
        try {
            return discovery.get(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchDiscoveryController.class, "get(long)");
        }
    }
}
