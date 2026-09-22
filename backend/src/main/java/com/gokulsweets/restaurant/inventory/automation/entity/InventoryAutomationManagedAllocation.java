package com.gokulsweets.restaurant.inventory.automation.entity;

import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_automation_managed_allocations")
@Getter
@Setter
public class InventoryAutomationManagedAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false, unique = true)
    private InventoryDailyAllocation allocation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "automation_rule_id", nullable = false)
    private InventoryAutomationRule automationRule;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_run_id", nullable = false)
    private InventoryAutomationRun lastRun;
    @Column(name = "last_automated_at", nullable = false)
    private LocalDateTime lastAutomatedAt;
}
