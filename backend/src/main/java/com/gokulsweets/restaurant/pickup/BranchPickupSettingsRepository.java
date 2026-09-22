package com.gokulsweets.restaurant.pickup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchPickupSettingsRepository
        extends JpaRepository<BranchPickupSettings, Long> {

    Optional<BranchPickupSettings> findByBranchId(Long branchId);
}