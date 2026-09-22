package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class InventoryQuantityService {

    private static final int QUANTITY_SCALE = 3;

    public BigDecimal normalizeNonNegative(
            BigDecimal quantity,
            InventoryUnit unit,
            String fieldName
    ) {
        if (quantity == null) {
            return null;
        }

        BigDecimal normalized;

        try {
            normalized = quantity.setScale(
                    QUANTITY_SCALE,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new InventoryConflictException(
                    "INVALID_QUANTITY_PRECISION",
                    fieldName + " supports at most three decimal places."
            );
        }

        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new InventoryConflictException(
                    "NEGATIVE_QUANTITY",
                    fieldName + " cannot be negative."
            );
        }

        if (
                unit == InventoryUnit.PIECE
                        && normalized.stripTrailingZeros().scale() > 0
        ) {
            throw new InventoryConflictException(
                    "FRACTIONAL_PIECE_QUANTITY",
                    fieldName + " must be a whole number for piece-based products."
            );
        }

        return normalized;
    }

    public BigDecimal normalizePositive(
            BigDecimal quantity,
            InventoryUnit unit,
            String fieldName
    ) {
        BigDecimal normalized = normalizeNonNegative(
                quantity,
                unit,
                fieldName
        );

        if (
                normalized == null
                        || normalized.compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new InventoryConflictException(
                    "POSITIVE_QUANTITY_REQUIRED",
                    fieldName + " must be greater than zero."
            );
        }

        return normalized;
    }

    public void validatePolicyUnit(
            ProductSaleMode saleMode,
            InventoryControlMode controlMode,
            InventoryUnit inventoryUnit
    ) {
        if (controlMode == InventoryControlMode.SLOT_CAPACITY) {
            if (inventoryUnit != InventoryUnit.CAPACITY_POINT) {
                throw new InventoryConflictException(
                        "INVALID_SLOT_CAPACITY_UNIT",
                        "Slot-capacity products must use CAPACITY_POINT as the inventory unit."
                );
            }
            return;
        }

        InventoryUnit requiredUnit =
                saleMode == ProductSaleMode.WEIGHT
                        ? InventoryUnit.GRAM
                        : InventoryUnit.PIECE;

        if (inventoryUnit != requiredUnit) {
            throw new InventoryConflictException(
                    "INVENTORY_UNIT_MISMATCH",
                    saleMode == ProductSaleMode.WEIGHT
                            ? "Weight-based products must use GRAM as the inventory unit."
                            : "Unit-based products must use PIECE as the inventory unit."
            );
        }
    }
}

