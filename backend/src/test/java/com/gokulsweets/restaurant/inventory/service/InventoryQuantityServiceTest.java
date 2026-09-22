package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryQuantityServiceTest {

    private final InventoryQuantityService service =
            new InventoryQuantityService();

    @Test
    void rejectsFractionalPieceQuantity() {
        assertThatThrownBy(() ->
                service.normalizePositive(
                        new BigDecimal("2.500"),
                        InventoryUnit.PIECE,
                        "Approved quantity"
                )
        )
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void keepsGramQuantityAtCanonicalScale() {
        BigDecimal result = service.normalizePositive(
                new BigDecimal("1250"),
                InventoryUnit.GRAM,
                "Approved quantity"
        );

        assertThat(result).isEqualByComparingTo("1250.000");
        assertThat(result.scale()).isEqualTo(3);
    }

    @Test
    void weightProductsMustUseGrams() {
        assertThatThrownBy(() ->
                service.validatePolicyUnit(
                        ProductSaleMode.WEIGHT,
                        InventoryControlMode.DAILY_PRODUCTION,
                        InventoryUnit.PIECE
                )
        )
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("GRAM");
    }
}

