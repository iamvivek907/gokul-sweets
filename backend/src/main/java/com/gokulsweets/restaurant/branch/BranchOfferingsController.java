package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for branch offerings operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/offerings")
public class BranchOfferingsController {

    private final BranchOfferingsService service;

    /**
     * Returns the operation.
     *
     * @param branchId the branch id
     * @return the get result
     */
    @GetMapping
    public BranchOfferingsService.Snapshot get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsController.class, "get(long)");
        try {
            return service.admin(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsController.class, "get(long)");
        }
    }

    /**
     * Saves the operation.
     *
     * @param branchId the branch id
     * @param version the version
     * @param input the input
     * @return the save result
     */
    @PutMapping
    public BranchOfferingsService.Snapshot save(
            @PathVariable long branchId,
            @RequestHeader("If-Match") long version,
            @Valid @RequestBody BranchOfferingsService.Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchOfferingsController.class,
                        "save(long,long,BranchOfferingsService.Input)");
        try {
            return service.save(branchId, input, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchOfferingsController.class,
                    "save(long,long,BranchOfferingsService.Input)");
        }
    }

    /**
     * Publishes the operation.
     *
     * @param branchId the branch id
     * @param version the version
     * @return the publish result
     */
    @PostMapping("/publish")
    public BranchOfferingsService.Snapshot publish(
            @PathVariable long branchId, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsController.class, "publish(long,long)");
        try {
            return service.publish(branchId, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchOfferingsController.class,
                    "publish(long,long)");
        }
    }
}
