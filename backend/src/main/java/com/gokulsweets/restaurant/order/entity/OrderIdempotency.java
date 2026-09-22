package com.gokulsweets.restaurant.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_order_idempotency_order",
                        columnNames = "order_id"
                )
        }
)
@Getter
@Setter
public class OrderIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            unique = true
    )
    private Order order;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}