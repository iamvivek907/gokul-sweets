package com.gokulsweets.restaurant.inventory.entity;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_stock_transactions")
@Getter
@Setter
public class InventoryStockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_product_id", nullable = false)
    private BranchProduct branchProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private InventoryDailyAllocation allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private InventoryReservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private InventoryTransactionType transactionType;

    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "reference_key", length = 100)
    private String referenceKey;

    @Column(length = 500)
    private String reason;

    @Column(name = "performed_by", length = 150)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

