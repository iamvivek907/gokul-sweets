package com.gokulsweets.restaurant.inventory.automation.entity;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationRunStatus;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_automation_runs")
@Getter
@Setter
public class InventoryAutomationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "through_date", nullable = false)
    private LocalDate throughDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private InventoryAutomationTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false, length = 20)
    private InventoryAutomationRunStatus runStatus;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount = 0;
    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount = 0;
    @Column(name = "suggested_count", nullable = false)
    private Integer suggestedCount = 0;
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount = 0;
    @Column(name = "error_count", nullable = false)
    private Integer errorCount = 0;

    @Column(name = "initiated_by", nullable = false, length = 150)
    private String initiatedBy;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "error_summary", length = 500)
    private String errorSummary;
}
