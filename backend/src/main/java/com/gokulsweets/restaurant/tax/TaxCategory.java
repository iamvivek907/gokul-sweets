package com.gokulsweets.restaurant.tax;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tax_categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tax_categories_code",
                        columnNames = "code"
                )
        }
)
@Getter
@Setter
public class TaxCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            unique = true,
            length = 80
    )
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "hsn_sac_code", length = 20)
    private String hsnSacCode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

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
