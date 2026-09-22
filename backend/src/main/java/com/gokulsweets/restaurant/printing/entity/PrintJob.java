package com.gokulsweets.restaurant.printing.entity;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrintJobType;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "print_jobs"
)
@Getter
@Setter
public class PrintJob {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;


    @ManyToOne(
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "printer_id"
    )
    private PrinterDevice printer;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "kot_id",
            nullable = false
    )
    private Kot kot;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            name = "job_type",
            nullable = false,
            length = 40
    )
    private PrintJobType jobType;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 40
    )
    private PrintJobPurpose purpose;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 50
    )
    private PrinterStation station;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 30
    )
    private PrintJobStatus status;


    @Column(
            nullable = false
    )
    private Integer copies =
            1;


    @Column(
            name = "attempt_count",
            nullable = false
    )
    private Integer attemptCount =
            0;


    @Column(
            name = "max_attempts",
            nullable = false
    )
    private Integer maxAttempts =
            5;


    @Column(
            name = "claim_token",
            length = 100
    )
    private String claimToken;


    @Column(
            name = "claimed_by_agent",
            length = 120
    )
    private String claimedByAgent;


    @Column(
            name = "claimed_at"
    )
    private LocalDateTime claimedAt;


    @Column(
            name = "claim_expires_at"
    )
    private LocalDateTime claimExpiresAt;


    @Column(
            name = "queued_at",
            nullable = false
    )
    private LocalDateTime queuedAt;


    @Column(
            name = "first_attempt_at"
    )
    private LocalDateTime firstAttemptAt;


    @Column(
            name = "last_attempt_at"
    )
    private LocalDateTime lastAttemptAt;


    @Column(
            name = "printed_at"
    )
    private LocalDateTime printedAt;


    @Column(
            name = "failed_at"
    )
    private LocalDateTime failedAt;


    @Column(
            name = "next_attempt_at"
    )
    private LocalDateTime nextAttemptAt;


    @Column(
            name = "last_error_code",
            length = 100
    )
    private String lastErrorCode;


    @Column(
            name = "last_error_message",
            length = 500
    )
    private String lastErrorMessage;


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


        if (
                status
                        ==
                        null
        ) {

            status =
                    PrintJobStatus.QUEUED;
        }


        if (
                copies
                        ==
                        null
        ) {

            copies =
                    1;
        }


        if (
                attemptCount
                        ==
                        null
        ) {

            attemptCount =
                    0;
        }


        if (
                maxAttempts
                        ==
                        null
        ) {

            maxAttempts =
                    5;
        }


        if (
                queuedAt
                        ==
                        null
        ) {

            queuedAt =
                    now;
        }


        if (
                createdAt
                        ==
                        null
        ) {

            createdAt =
                    now;
        }


        updatedAt =
                now;
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}