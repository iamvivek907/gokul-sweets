package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates branch operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;

    private final BranchExperienceService experience;

    private final BranchPickupSettingsRepository pickupSettings;

    private final EnhancementProperties features;

    /**
     * Returns response information for branch.
     *
     * @param branch the branch supplied to this method
     * @return the {@code BranchResponse} result
     */
    private BranchResponse response(Branch branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchService.class, "response(Branch)");
        try {
            var published =
                    features.isBranchExperience() ? experience.published(branch.getId()) : null;
            boolean pickup =
                    pickupSettings
                            .findByBranchId(branch.getId())
                            .map(setting -> setting.isEnabled())
                            .orElse(false);
            return BranchResponse.from(
                    branch,
                    published == null ? null : published.imageUrl(),
                    published == null ? null : published.mobileUrl(),
                    published == null ? null : published.altText(),
                    published == null ? null : published.description(),
                    pickup);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BranchService.class, "response(Branch)");
        }
    }

    /**
     * Returns active branches.
     *
     * @return the get active branches result
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> getActiveBranches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchService.class, "getActiveBranches()");
        try {
            List<BranchResponse> branches =
                    branchRepository.findByActiveTrueOrderByNameAsc().stream()
                            .map(this::response)
                            .toList();
            log.debug("Loaded active customer branches: count={}", branches.size());
            return branches;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchService.class, "getActiveBranches()");
        }
    }

    /**
     * Returns branch.
     *
     * @param id the id
     * @return the get branch result
     */
    @Transactional(readOnly = true)
    public BranchResponse getBranch(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchService.class, "getBranch(Long)");
        try {
            if (id == null) {
                throw new IllegalArgumentException("Branch ID is required.");
            }
            Branch branch =
                    branchRepository
                            .findByIdAndActiveTrue(id)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Customer requested unavailable branch:"
                                                        + " branchId={}",
                                                id);
                                        return new IllegalArgumentException(
                                                "Selected branch is unavailable.");
                                    });
            return response(branch);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BranchService.class, "getBranch(Long)");
        }
    }
}
