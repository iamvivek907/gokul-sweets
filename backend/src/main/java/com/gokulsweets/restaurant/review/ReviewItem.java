package com.gokulsweets.restaurant.review;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.Product;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persisted review item state. */
@Entity
@Table(name = "review_items")
@Getter
@Setter
public class ReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ons create. */
    @PrePersist
    void onCreate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(ReviewItem.class, "onCreate()");
        try {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ReviewItem.class, "onCreate()");
        }
    }

    /** Ons update. */
    @PreUpdate
    void onUpdate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(ReviewItem.class, "onUpdate()");
        try {
            updatedAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ReviewItem.class, "onUpdate()");
        }
    }
}
