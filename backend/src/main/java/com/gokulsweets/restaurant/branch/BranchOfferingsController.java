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
     * Handles {@code GET /api/admin/branches/{branchId}/offerings} for branch offerings.
     *
     * <p>Delegates to {@code service.admin(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code service.admin(branchId)}
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
     * Handles {@code PUT /api/admin/branches/{branchId}/offerings} for branch offerings.
     *
     * <p>Delegates to {@code service.save(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param version the version supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code service.save(branchId, input, version)}
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
     * Handles {@code POST /api/admin/branches/{branchId}/offerings/publish} for branch offerings.
     *
     * <p>Delegates to {@code service.publish(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code service.publish(branchId, version)}
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
