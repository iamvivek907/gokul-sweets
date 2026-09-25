package com.gokulsweets.restaurant.order.entity;

import com.gokulsweets.restaurant.config.ApplicationClock;

import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_product_id", columnList = "product_id")
        }
)
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_mode", nullable = false, length = 20)
    private ProductSaleMode saleMode = ProductSaleMode.UNIT;

    /* Always 1 for a WEIGHT item. */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    /* Price per item for UNIT; price per kilogram for WEIGHT. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ApplicationClock.legacyTimestampNow();
    }
}
