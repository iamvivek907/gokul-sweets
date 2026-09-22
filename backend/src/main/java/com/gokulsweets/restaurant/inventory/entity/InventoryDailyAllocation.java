package com.gokulsweets.restaurant.inventory.entity;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_daily_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_allocation_product_date",
                columnNames = {
                        "branch_product_id",
                        "service_date"
                }
        )
)
@Getter
@Setter
public class InventoryDailyAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_product_id", nullable = false)
    private BranchProduct branchProduct;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryAllocationStatus status =
            InventoryAllocationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_unit", nullable = false, length = 20)
    private InventoryUnit inventoryUnit;

    @Column(name = "approved_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal approvedQuantity = BigDecimal.ZERO;

    @Column(name = "ready_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal readyQuantity = BigDecimal.ZERO;

    @Column(name = "safety_buffer_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal safetyBufferQuantity = BigDecimal.ZERO;

    @Column(name = "held_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal heldQuantity = BigDecimal.ZERO;

    @Column(name = "committed_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal committedQuantity = BigDecimal.ZERO;

    @Column(name = "fulfilled_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    @Column(name = "wasted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal wastedQuantity = BigDecimal.ZERO;

    @Column(name = "forecast_quantity", precision = 14, scale = 3)
    private BigDecimal forecastQuantity;

    @Column(name = "forecast_confidence", length = 20)
    private String forecastConfidence;

    @Column(name = "expected_ready_at")
    private LocalDateTime expectedReadyAt;

    @Column(name = "actual_ready_at")
    private LocalDateTime actualReadyAt;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String note;

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

