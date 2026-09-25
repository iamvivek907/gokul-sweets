package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.ApplicationClock;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "pickup_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pickup_slots_branch_date_time",
                        columnNames = {
                                "branch_id",
                                "slot_date",
                                "start_time",
                                "end_time"
                        }
                )
        }
)
@Getter
@Setter
public class PickupSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private LocalDate slotDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer bookedCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = ApplicationClock.legacyTimestampNow();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ApplicationClock.legacyTimestampNow();
    }

    @Column(nullable = false)
    private boolean priorityEnabled = false;

    @Column(nullable = false)
    private Integer priorityCapacity = 0;

    @Column(nullable = false)
    private Integer priorityBookedCount = 0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priorityCharge = BigDecimal.ZERO;
}
