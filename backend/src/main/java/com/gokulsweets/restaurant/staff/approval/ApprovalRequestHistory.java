package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_request_history")
@Getter
@Setter
public class ApprovalRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "approval_request_id",
            nullable = false
    )
    private ApprovalRequest approvalRequest;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private ApprovalRequestAction action;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "from_status",
            length = 30
    )
    private ApprovalRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "to_status",
            nullable = false,
            length = 30
    )
    private ApprovalRequestStatus toStatus;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "actor_staff_user_id",
            nullable = false
    )
    private StaffUser actorStaffUser;

    @Column(
            name = "actor_name",
            nullable = false,
            length = 150
    )
    private String actorName;

    @Column(length = 1000)
    private String comment;

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
