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
        name = "staff_payroll_opening_balances",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uq_staff_payroll_opening_staff",
                columnNames = {
                        "staff_user_id"
                }
        )
)
@Getter
@Setter
public class StaffPayrollOpeningBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "staff_user_id",
            nullable = false,
            unique = true
    )
    private StaffUser staffUser;

    @Column(
            name = "as_of_date",
            nullable = false
    )
    private LocalDate asOfDate;

    @Column(
            name = "earned_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal earnedAmount;

    @Column(
            name = "taken_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal takenAmount;

    @Column(length = 1000)
    private String note;

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
