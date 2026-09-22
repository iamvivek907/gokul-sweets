package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.attendance.AttendanceType;
import com.gokulsweets.restaurant.staff.attendance.StaffAttendance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_earnings_ledger")
@Getter
@Setter
public class StaffEarning {

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

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "attendance_id",
            nullable = false,
            unique = true
    )
    private StaffAttendance attendance;

    @Column(
            name = "earning_date",
            nullable = false
    )
    private LocalDate earningDate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "attendance_type",
            nullable = false,
            length = 30
    )
    private AttendanceType attendanceType;

    @Column(
            name = "rate_snapshot",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal rateSnapshot;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

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
