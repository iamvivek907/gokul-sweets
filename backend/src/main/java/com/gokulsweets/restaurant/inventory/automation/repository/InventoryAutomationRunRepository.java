package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRun;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence operations for inventory automation run records. */
public interface InventoryAutomationRunRepository
        extends JpaRepository<InventoryAutomationRun, Long> {

    /**
     * Performs the find top20 by branch id order by started at desc operation for inventory
     * automation run repository.
     *
     * @param branchId the branch id
     * @return the find top20 by branch id order by started at desc result
     */
    @EntityGraph(attributePaths = {"branch"})
    List<InventoryAutomationRun> findTop20ByBranchIdOrderByStartedAtDesc(Long branchId);
}
