package com.gokulsweets.restaurant.branchproduct.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record AdminBranchProductUpdateRequest(

        Boolean available,

        @DecimalMin(
                value = "0.01",
                inclusive = true,
                message = "Price override must be greater than zero."
        )
        BigDecimal priceOverride,

        Boolean clearPriceOverride,

        @Min(
                value = 0,
                message = "Display order cannot be negative."
        )
        Integer displayOrder

) {

    @AssertTrue(
            message = "priceOverride and clearPriceOverride cannot be used together."
    )
    public boolean isPriceUpdateValid() {

        return !(
                priceOverride != null
                        &&
                        Boolean.TRUE.equals(
                                clearPriceOverride
                        )
        );
    }
}