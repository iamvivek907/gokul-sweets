package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.branch.Branch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "branch_pickup_settings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_branch_pickup_settings_branch",
                        columnNames = "branch_id"
                )
        }
)
@Getter
@Setter
public class BranchPickupSettings {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @Column(nullable = false)
    private Integer slotDurationMinutes = 30;

    @Column(nullable = false)
    private Integer defaultCapacity = 10;

    @Column(nullable = false)
    private Integer advanceBookingDays = 7;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private LocalTime openingTime;

    @Column(nullable = false)
    private LocalTime closingTime;

    /*
     * =========================================================
     * PRIORITY PICKUP DEFAULTS
     * =========================================================
     *
     * These values are defaults for newly generated slots.
     *
     * Existing pickup slots keep their own configuration and
     * are not modified when branch defaults change.
     */

    @Column(nullable = false)
    private boolean priorityEnabled = false;

    @Column(nullable = false)
    private Integer defaultPriorityCapacity = 0;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal defaultPriorityCharge =
            BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}