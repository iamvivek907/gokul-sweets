package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchExperienceService experience;
    private final BranchPickupSettingsRepository pickupSettings;
    private final EnhancementProperties features;

    private BranchResponse response(Branch branch) {
        var published = features.isBranchExperience() ? experience.published(branch.getId()) : null;
        boolean pickup = pickupSettings.findByBranchId(branch.getId())
                .map(setting -> setting.isEnabled()).orElse(false);
        return BranchResponse.from(branch, published == null ? null : published.imageUrl(),
                published == null ? null : published.mobileUrl(),
                published == null ? null : published.altText(),
                published == null ? null : published.description(), pickup);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> getActiveBranches() {

        List<BranchResponse> branches =
                branchRepository
                        .findByActiveTrueOrderByNameAsc()
                        .stream()
                        .map(this::response)
                        .toList();

        log.debug(
                "Loaded active customer branches: count={}",
                branches.size()
        );

        return branches;
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranch(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }

        Branch branch =
                branchRepository
                        .findByIdAndActiveTrue(id)
                        .orElseThrow(() -> {
                            log.warn(
                                    "Customer requested unavailable branch: branchId={}",
                                    id
                            );

                            return new IllegalArgumentException(
                                    "Selected branch is unavailable."
                            );
                        });

        return response(branch);
    }
}
