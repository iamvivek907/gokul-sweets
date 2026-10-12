package com.gokulsweets.restaurant.pickup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for branch pickup settings records. */
public interface BranchPickupSettingsRepository extends JpaRepository<BranchPickupSettings, Long> {

    /**
     * Finds by branch id.
     *
     * @param branchId the branch id
     * @return the find by branch id result
     */
    Optional<BranchPickupSettings> findByBranchId(Long branchId);
}
