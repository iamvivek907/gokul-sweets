package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "request_number",
            unique = true,
            length = 30
    )
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "request_type",
            nullable = false,
            length = 30
    )
    private ApprovalRequestType requestType;

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

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private ApprovalRequestStatus status;

    @Column(
            nullable = false,
            length = 180
    )
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(
            name = "workflow_version",
            nullable = false
    )
    private Integer workflowVersion = 1;

    @Column(
            name = "submitted_at",
            nullable = false
    )
    private LocalDateTime submittedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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
