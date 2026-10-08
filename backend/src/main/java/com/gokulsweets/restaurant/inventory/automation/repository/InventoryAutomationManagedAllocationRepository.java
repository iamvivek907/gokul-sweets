package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationManagedAllocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for inventory automation managed allocation records. */
public interface InventoryAutomationManagedAllocationRepository
        extends JpaRepository<InventoryAutomationManagedAllocation, Long> {

    /**
     * Performs the find by allocation id operation for inventory automation managed allocation
     * repository.
     *
     * @param allocationId the allocation id
     * @return the find by allocation id result
     */
    @EntityGraph(attributePaths = {"automationRule", "lastRun"})
    Optional<InventoryAutomationManagedAllocation> findByAllocationId(Long allocationId);

    /**
     * Performs the find by allocation id in operation for inventory automation managed allocation
     * repository.
     *
     * @param allocationIds the allocation ids
     * @return the find by allocation id in result
     */
    @EntityGraph(attributePaths = {"allocation", "automationRule", "lastRun"})
    List<InventoryAutomationManagedAllocation> findByAllocationIdIn(Collection<Long> allocationIds);
}
