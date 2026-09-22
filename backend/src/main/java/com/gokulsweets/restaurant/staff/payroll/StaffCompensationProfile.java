package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "staff_compensation_profiles",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uq_staff_compensation_effective",
                columnNames = {
                        "staff_user_id",
                        "effective_from"
                }
        )
)
@Getter
@Setter
public class StaffCompensationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "staff_user_id",
            nullable = false
    )
    private StaffUser staffUser;

    @Column(
            name = "effective_from",
            nullable = false
    )
    private LocalDate effectiveFrom;

    @Column(
            name = "daily_rate",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal dailyRate;

    @Column(
            name = "half_day_rate",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal halfDayRate;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "created_by_staff_user_id",
            nullable = false
    )
    private StaffUser createdByStaffUser;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        createdAt =
                LocalDateTime.now();
    }
}
