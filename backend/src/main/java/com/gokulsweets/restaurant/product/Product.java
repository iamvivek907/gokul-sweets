package com.gokulsweets.restaurant.product;

import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.tax.TaxCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_products_code",
                        columnNames = "code"
                )
        }
)
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    private String code;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "category_id",
            nullable = false
    )
    private Category category;


    @Column(
            nullable = false,
            length = 150
    )
    private String name;


    @Column(length = 500)
    private String description;


    /*
     * UNIT:
     * Price for one item.
     *
     * WEIGHT:
     * Price for one kilogram.
     */
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal basePrice;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "sale_mode",
            nullable = false,
            length = 20
    )
    private ProductSaleMode saleMode =
            ProductSaleMode.UNIT;


    @Column(name = "minimum_weight_grams")
    private Integer minimumWeightGrams;


    @Column(name = "weight_step_grams")
    private Integer weightStepGrams;


    @Column(nullable = false)
    private boolean active = true;


    /*
     * Cloudflare R2 public image URL.
     *
     * Example:
     *
     * https://pub-1486d596635f4a65aa86b4afd04bec85.r2.dev/products/15.jpg
     */
    @Column(
            name = "image_url",
            length = 1000
    )
    private String imageUrl;


    @Column(nullable = false)
    private LocalDateTime createdAt;


    @Column(nullable = false)
    private LocalDateTime updatedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_category_id")
    private TaxCategory taxCategory;


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