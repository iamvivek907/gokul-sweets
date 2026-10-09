package com.gokulsweets.restaurant.inventory.automation.entity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Persisted inventory availability window state. */
@Entity
@Table(name = "inventory_availability_windows")
@Getter
@Setter
public class InventoryAvailabilityWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "automation_rule_id", nullable = false)
    private InventoryAutomationRule automationRule;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ons create. */
    @PrePersist
    protected void onCreate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryAvailabilityWindow.class, "onCreate()");
        try {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryAvailabilityWindow.class, "onCreate()");
        }
    }

    /** Ons update. */
    @PreUpdate
    protected void onUpdate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryAvailabilityWindow.class, "onUpdate()");
        try {
            updatedAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryAvailabilityWindow.class, "onUpdate()");
        }
    }
}
