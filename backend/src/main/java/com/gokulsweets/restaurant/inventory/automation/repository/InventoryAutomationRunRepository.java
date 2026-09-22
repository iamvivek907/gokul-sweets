package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAutomationRunRepository
        extends JpaRepository<InventoryAutomationRun, Long> {
    @EntityGraph(attributePaths = {"branch"})
    List<InventoryAutomationRun> findTop20ByBranchIdOrderByStartedAtDesc(Long branchId);
}
