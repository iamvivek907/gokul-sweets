package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rebates")
@Getter
@Setter
public class Rebate {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            nullable = false,
            unique = true,
            length = 50
    )
    private String code;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private RebateScope scope;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private RebateVisibility visibility =
            RebateVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "rebate_type",
            nullable = false,
            length = 30
    )
    private RebateType rebateType;

    @Column(
            name = "rebate_value",
            precision = 12,
            scale = 2
    )
    private BigDecimal rebateValue;

    @Column(
            name = "minimum_order_amount",
            precision = 12,
            scale = 2
    )
    private BigDecimal minimumOrderAmount;

    @Column(
            name = "maximum_discount_amount",
            precision = 12,
            scale = 2
    )
    private BigDecimal maximumDiscountAmount;

    @Column(name = "max_total_uses")
    private Integer maxTotalUses;

    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(
            name = "valid_from",
            nullable = false
    )
    private LocalDateTime validFrom;

    @Column(
            name = "valid_until",
            nullable = false
    )
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "created_by",
            nullable = false
    )
    private StaffUser createdBy;

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

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}
