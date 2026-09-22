package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationManagedAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryAutomationManagedAllocationRepository
        extends JpaRepository<InventoryAutomationManagedAllocation, Long> {
    @EntityGraph(attributePaths = {"automationRule", "lastRun"})
    Optional<InventoryAutomationManagedAllocation> findByAllocationId(Long allocationId);

    @EntityGraph(attributePaths = {"allocation", "automationRule", "lastRun"})
    List<InventoryAutomationManagedAllocation> findByAllocationIdIn(
            Collection<Long> allocationIds
    );
}
