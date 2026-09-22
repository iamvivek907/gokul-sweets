package com.gokulsweets.restaurant.inventory.automation.entity;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationMode;
import com.gokulsweets.restaurant.inventory.automation.enums.InventorySeasonalMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_automation_rules")
@Getter
@Setter
public class InventoryAutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_product_id", nullable = false, unique = true)
    private BranchProduct branchProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_mode", nullable = false, length = 40)
    private InventoryAutomationMode automationMode = InventoryAutomationMode.CREATE_DRAFT;

    @Column(name = "guaranteed_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal guaranteedQuantity = BigDecimal.ZERO;

    @Column(name = "forecast_enabled", nullable = false)
    private boolean forecastEnabled = true;

    @Column(name = "lookback_weeks", nullable = false)
    private Integer lookbackWeeks = 8;

    @Column(name = "minimum_history_days", nullable = false)
    private Integer minimumHistoryDays = 3;

    @Column(name = "demand_multiplier", nullable = false, precision = 8, scale = 3)
    private BigDecimal demandMultiplier = BigDecimal.ONE;

    @Column(name = "maximum_suggested_quantity", precision = 14, scale = 3)
    private BigDecimal maximumSuggestedQuantity;

    @Column(name = "available_days_mask", nullable = false)
    private Integer availableDaysMask = 127;

    @Enumerated(EnumType.STRING)
    @Column(name = "seasonal_mode", nullable = false, length = 30)
    private InventorySeasonalMode seasonalMode = InventorySeasonalMode.ALWAYS;

    @Column(name = "generation_horizon_days")
    private Integer generationHorizonDays;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
