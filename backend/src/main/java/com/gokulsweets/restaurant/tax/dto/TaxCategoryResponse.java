package com.gokulsweets.restaurant.tax.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.tax.TaxCategory;

import java.math.BigDecimal;

/** Immutable tax category response data contract. */
public record TaxCategoryResponse(
        Long id,
        String code,
        String name,
        String hsnSacCode,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate,
        boolean active) {

    /**
     * Froms the operation.
     *
     * @param taxCategory the tax category
     * @return the from result
     */
    public static TaxCategoryResponse from(TaxCategory taxCategory) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCategoryResponse.class, "from(TaxCategory)");
        try {
            return new TaxCategoryResponse(
                    taxCategory.getId(),
                    taxCategory.getCode(),
                    taxCategory.getName(),
                    taxCategory.getHsnSacCode(),
                    taxCategory.getCgstRate(),
                    taxCategory.getSgstRate(),
                    taxCategory.getIgstRate(),
                    taxCategory.isActive());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCategoryResponse.class, "from(TaxCategory)");
        }
    }
}
