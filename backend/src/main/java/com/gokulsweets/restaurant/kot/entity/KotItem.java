package com.gokulsweets.restaurant.kot.entity;

import com.gokulsweets.restaurant.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "kot_items",
        indexes = {
                @Index(
                        name = "idx_kot_items_kot_id",
                        columnList = "kot_id"
                ),
                @Index(
                        name = "idx_kot_items_product_id",
                        columnList = "product_id"
                )
        }
)
@Getter
@Setter
public class KotItem {

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
            name = "kot_id",
            nullable = false
    )
    private Kot kot;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;


    /*
     * Product-name snapshot.
     *
     * Kitchen history must continue showing
     * the name that existed when this KOT
     * was created.
     */
    @Column(
            name = "product_name",
            nullable = false,
            length = 200
    )
    private String productName;


    @Column(
            nullable = false
    )
    private Integer quantity;


    @Column(
            name = "display_order",
            nullable = false
    )
    private Integer displayOrder = 0;
}