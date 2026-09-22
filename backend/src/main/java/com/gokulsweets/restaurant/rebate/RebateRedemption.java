package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.order.entity.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rebate_redemptions")
@Getter
@Setter
public class RebateRedemption {

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

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false,
            unique = true
    )
    private Order order;

    @Column(
            name = "customer_phone",
            nullable = false,
            length = 20
    )
    private String customerPhone;

    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal discountAmount;

    @Column(
            name = "redeemed_at",
            nullable = false
    )
    private LocalDateTime redeemedAt;

    @PrePersist
    protected void onCreate() {

        redeemedAt =
                LocalDateTime.now();
    }
}