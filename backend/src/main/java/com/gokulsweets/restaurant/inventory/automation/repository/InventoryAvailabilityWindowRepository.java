package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAvailabilityWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InventoryAvailabilityWindowRepository
        extends JpaRepository<InventoryAvailabilityWindow, Long> {
    List<InventoryAvailabilityWindow> findByAutomationRuleIdInAndActiveTrue(
            Collection<Long> ruleIds
    );
    void deleteByAutomationRuleId(Long ruleId);
}
