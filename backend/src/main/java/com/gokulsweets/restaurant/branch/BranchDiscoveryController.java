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
    @GetMapping("/reviews")
    public BranchDiscoveryService.PublicReviewPage reviews(
            @PathVariable long branchId, @RequestParam(required = false) Long before) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchDiscoveryController.class, "reviews(long,Long)");
        try {
            return discovery.reviews(branchId, before);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchDiscoveryController.class,
                    "reviews(long,Long)");
        }
    }

    /** Returns the published discovery summary for a branch. */
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

    /** Reads one public dish-review page. */
    @GetMapping("/dish-reviews")
    public BranchDiscoveryService.DishReviewPage dishReviews(
            @PathVariable long branchId,
            @RequestParam java.util.List<Long> productIds,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer stars) {
        long started =
                MethodTiming.start(
                        BranchDiscoveryController.class,
                        "dishReviews(long,java.util.List<Long>,Long,Integer)");
        try {
            return discovery.dishReviews(branchId, productIds, before, stars);
        } finally {
            MethodTiming.finish(
                    started,
                    BranchDiscoveryController.class,
                    "dishReviews(long,java.util.List<Long>,Long,Integer)");
        }
    }
}
