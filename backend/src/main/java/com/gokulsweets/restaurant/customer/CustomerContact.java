package com.gokulsweets.restaurant.customer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_contacts",
        indexes = {
                @Index(
                        name = "idx_customer_contacts_last_seen_at",
                        columnList = "last_seen_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_customer_contacts_normalized_phone",
                        columnNames = "normalized_phone"
                )
        }
)
@Getter
@Setter
public class CustomerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "normalized_phone",
            nullable = false,
            unique = true,
            length = 20
    )
    private String normalizedPhone;

    @Column(
            name = "latest_name",
            length = 150
    )
    private String latestName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "verification_status",
            nullable = false,
            length = 30
    )
    private CustomerContactStatus verificationStatus =
            CustomerContactStatus.UNVERIFIED;

    @Column(
            name = "first_seen_at",
            nullable = false
    )
    private LocalDateTime firstSeenAt;

    @Column(
            name = "last_seen_at",
            nullable = false
    )
    private LocalDateTime lastSeenAt;

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
                firstSeenAt == null
        ) {

            firstSeenAt =
                    now;
        }

        if (
                lastSeenAt == null
        ) {

            lastSeenAt =
                    now;
        }

        createdAt =
                now;

        updatedAt =
                now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}
