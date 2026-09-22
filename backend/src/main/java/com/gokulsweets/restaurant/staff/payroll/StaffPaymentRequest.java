package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_payment_requests")
@Getter
@Setter
public class StaffPaymentRequest {

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
            name = "approval_request_id",
            nullable = false,
            unique = true
    )
    private ApprovalRequest approvalRequest;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

    @Column(length = 1000)
    private String note;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
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
