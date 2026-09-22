package com.gokulsweets.restaurant.rebate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "rebate_customers")
@IdClass(RebateCustomerId.class)
@Getter
@Setter
public class RebateCustomer {

    @Id
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "rebate_id",
            nullable = false
    )
    private Rebate rebate;

    @Id
    @Column(
            name = "customer_phone",
            nullable = false,
            length = 20
    )
    private String customerPhone;

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