package com.gokulsweets.restaurant.staff.attendance;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequest;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Persisted staff attendance state. */
@Entity
@Table(name = "staff_attendance")
@Getter
@Setter
public class StaffAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private StaffUser staffUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_request_id", nullable = false, unique = true)
    private ApprovalRequest approvalRequest;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_type", nullable = false, length = 30)
    private AttendanceType attendanceType;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ons create. */
    @PrePersist
    protected void onCreate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendance.class, "onCreate()");
        try {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffAttendance.class, "onCreate()");
        }
    }

    /** Ons update. */
    @PreUpdate
    protected void onUpdate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAttendance.class, "onUpdate()");
        try {
            updatedAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffAttendance.class, "onUpdate()");
        }
    }
}
