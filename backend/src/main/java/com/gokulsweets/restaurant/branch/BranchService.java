package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
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

    @Transactional(readOnly = true)
    public List<BranchResponse> getActiveBranches() {

        List<BranchResponse> branches =
                branchRepository
                        .findByActiveTrueOrderByNameAsc()
                        .stream()
                        .map(BranchResponse::from)
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

        return BranchResponse.from(branch);
    }
}