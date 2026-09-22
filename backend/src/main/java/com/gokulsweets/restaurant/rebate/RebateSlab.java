package com.gokulsweets.restaurant.rebate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rebate_slabs")
@Getter
@Setter
public class RebateSlab {

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
            name = "rebate_id",
            nullable = false
    )
    private Rebate rebate;

    @Column(
            name = "minimum_order_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal minimumOrderAmount;

    @Column(
            name = "rebate_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal rebateAmount;

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