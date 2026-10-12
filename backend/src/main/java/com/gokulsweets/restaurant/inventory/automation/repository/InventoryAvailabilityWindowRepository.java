package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAvailabilityWindow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Persistence operations for inventory availability window records. */
public interface InventoryAvailabilityWindowRepository
        extends JpaRepository<InventoryAvailabilityWindow, Long> {

    /**
     * Performs the find by automation rule id in and active true operation for inventory
     * availability window repository.
     *
     * @param ruleIds the rule ids
     * @return the find by automation rule id in and active true result
     */
    List<InventoryAvailabilityWindow> findByAutomationRuleIdInAndActiveTrue(
            Collection<Long> ruleIds);

    /**
     * Performs the delete by automation rule id operation for inventory availability window
     * repository.
     *
     * @param ruleId the rule id
     */
    void deleteByAutomationRuleId(Long ruleId);
}
