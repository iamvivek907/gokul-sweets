package com.gokulsweets.restaurant.tax.dto;

import com.gokulsweets.restaurant.tax.TaxCategory;

import java.math.BigDecimal;

public record TaxCategoryResponse(
        Long id,
        String code,
        String name,
        String hsnSacCode,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal igstRate,
        boolean active
) {

    public static TaxCategoryResponse from(
            TaxCategory taxCategory
    ) {
        return new TaxCategoryResponse(
                taxCategory.getId(),
                taxCategory.getCode(),
                taxCategory.getName(),
                taxCategory.getHsnSacCode(),
                taxCategory.getCgstRate(),
                taxCategory.getSgstRate(),
                taxCategory.getIgstRate(),
                taxCategory.isActive()
        );
    }
}
