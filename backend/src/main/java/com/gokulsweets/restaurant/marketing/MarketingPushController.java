package com.gokulsweets.restaurant.marketing;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Authorized branch-scoped campaign controls; evaluating never bypasses eligibility. */
@RestController
@RequestMapping("/api/admin/marketing-push/{branchId}")
@RequiredArgsConstructor
public class MarketingPushController {
    private final StaffAuthorizationService authorization;
    private final MarketingPushService marketing;

    /** Requires offer-management permission and access to the selected branch. */
    private void authorize(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushController.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.REBATE_MANAGE);
            authorization.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushController.class, "authorize(long)");
        }
    }

    /** Returns current controls, approved previews and anonymous send history. */
    @GetMapping
    public MarketingPushService.View get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushController.class, "get(long)");
        try {
            authorize(branchId);
            return marketing.view(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushController.class, "get(long)");
        }
    }

    /** Saves versioned branch settings; an upgrade never enables campaigns implicitly. */
    @PutMapping
    public MarketingPushService.Settings save(
            @PathVariable long branchId, @RequestBody MarketingPushService.Settings input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MarketingPushController.class, "save(long,MarketingPushService.Settings)");
        try {
            authorize(branchId);
            return marketing.save(branchId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MarketingPushController.class,
                    "save(long,MarketingPushService.Settings)");
        }
    }

    /**
     * Reserves eligible messages now; the regular delivery worker sends them with full rechecks.
     */
    @PostMapping("/evaluate")
    public Map<String, Integer> evaluate(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushController.class, "evaluate(long)");
        try {
            authorize(branchId);
            return Map.of("queued", marketing.evaluate(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushController.class, "evaluate(long)");
        }
    }
}
