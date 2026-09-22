package com.gokulsweets.restaurant.inventory.automation.repository;

import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAutomationRunItemRepository
        extends JpaRepository<InventoryAutomationRunItem, Long> {
}
