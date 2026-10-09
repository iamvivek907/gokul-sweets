package com.gokulsweets.restaurant.tax.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.tax.TaxCategory;

import java.math.BigDecimal;

/**
 * Immutable tax category response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param hsnSacCode the hsn sac code
 * @param cgstRate the cgst rate
 * @param sgstRate the sgst rate
 * @param igstRate the igst rate
 * @param active the active
 */
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
     * Maps the supplied data into a {@code TaxCategoryResponse} representation.
     *
     * @param taxCategory the tax category supplied to this method
     * @return the {@code TaxCategoryResponse} result
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
