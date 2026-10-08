package com.gokulsweets.restaurant.staff;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Persisted staff user state. */
@Entity
@Table(name = "staff_users")
@Getter
@Setter
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "staff_branch_access",
            joinColumns = @JoinColumn(name = "staff_user_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id"))
    private Set<Branch> branches = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ons create. */
    @PrePersist
    protected void onCreate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(StaffUser.class, "onCreate()");
        try {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffUser.class, "onCreate()");
        }
    }

    /** Ons update. */
    @PreUpdate
    protected void onUpdate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(StaffUser.class, "onUpdate()");
        try {
            updatedAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, StaffUser.class, "onUpdate()");
        }
    }
}
