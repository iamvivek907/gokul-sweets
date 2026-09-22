package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "branch_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_branch_products_branch_product",
                        columnNames = {"branch_id", "product_id"}
                )
        }
)
@Getter
@Setter
public class BranchProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(
            name = "price_override",
            precision = 12,
            scale = 2
    )
    private BigDecimal priceOverride;

    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}