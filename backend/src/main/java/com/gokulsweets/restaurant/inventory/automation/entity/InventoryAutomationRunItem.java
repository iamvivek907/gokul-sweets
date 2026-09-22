package com.gokulsweets.restaurant.inventory.automation.entity;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_automation_run_items")
@Getter
@Setter
public class InventoryAutomationRunItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private InventoryAutomationRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_product_id", nullable = false)
    private BranchProduct branchProduct;
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryAutomationOutcome outcome;
    @Column(name = "proposed_quantity", precision = 14, scale = 3)
    private BigDecimal proposedQuantity;
    @Column(name = "forecast_quantity", precision = 14, scale = 3)
    private BigDecimal forecastQuantity;
    @Column(length = 500)
    private String message;
}
