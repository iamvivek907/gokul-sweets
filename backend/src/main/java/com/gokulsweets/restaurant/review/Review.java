package com.gokulsweets.restaurant.review;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Persisted review state. */
@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PUBLISHED;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ons create. */
    @PrePersist
    void onCreate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(Review.class, "onCreate()");
        try {
            LocalDateTime now = LocalDateTime.now();
            createdAt = now;
            updatedAt = now;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, Review.class, "onCreate()");
        }
    }

    /** Ons update. */
    @PreUpdate
    void onUpdate() {
        final long __gokulMethodStartedNanos = MethodTiming.start(Review.class, "onUpdate()");
        try {
            updatedAt = LocalDateTime.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, Review.class, "onUpdate()");
        }
    }

    /**
     * Adds item.
     *
     * @param item the item
     */
    public void addItem(ReviewItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Review.class, "addItem(ReviewItem)");
        try {
            items.add(item);
            item.setReview(this);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, Review.class, "addItem(ReviewItem)");
        }
    }

    /** Clears items. */
    public void clearItems() {
        final long __gokulMethodStartedNanos = MethodTiming.start(Review.class, "clearItems()");
        try {
            items.forEach(item -> item.setReview(null));
            items.clear();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, Review.class, "clearItems()");
        }
    }

    /**
     * Removes item.
     *
     * @param item the item
     */
    public void removeItem(ReviewItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Review.class, "removeItem(ReviewItem)");
        try {
            items.remove(item);
            item.setReview(null);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, Review.class, "removeItem(ReviewItem)");
        }
    }
}
