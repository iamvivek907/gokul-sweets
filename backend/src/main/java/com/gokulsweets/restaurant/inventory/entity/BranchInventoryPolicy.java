package com.gokulsweets.restaurant.inventory.entity;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "branch_inventory_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_policy_branch_product",
                columnNames = "branch_product_id"
        )
)
@Getter
@Setter
public class BranchInventoryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_product_id", nullable = false)
    private BranchProduct branchProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_mode", nullable = false, length = 30)
    private InventoryControlMode controlMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_unit", nullable = false, length = 20)
    private InventoryUnit inventoryUnit;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled;

    @Column(name = "ready_stock_required", nullable = false)
    private boolean readyStockRequired;

    @Column(
            name = "default_safety_buffer",
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal defaultSafetyBuffer = BigDecimal.ZERO;

    @Column(
            name = "maximum_daily_allocation",
            precision = 14,
            scale = 3
    )
    private BigDecimal maximumDailyAllocation;

    @Column(name = "booking_horizon_days", nullable = false)
    private Integer bookingHorizonDays;

    @Column(name = "production_lead_minutes", nullable = false)
    private Integer productionLeadMinutes = 0;

    @Column(name = "shelf_life_minutes")
    private Integer shelfLifeMinutes;

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

