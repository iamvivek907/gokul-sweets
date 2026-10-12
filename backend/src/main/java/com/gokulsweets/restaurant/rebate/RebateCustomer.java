package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persisted rebate customer state. */
@Entity
@Table(name = "rebate_customers")
@IdClass(RebateCustomerId.class)
@Getter
@Setter
public class RebateCustomer {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rebate_id", nullable = false)
    private Rebate rebate;

    @Id
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Ons create. */
    @PrePersist
    protected void onCreate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateCustomer.class, "onCreate()");
        try {
            createdAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, RebateCustomer.class, "onCreate()");
        }
    }
}
