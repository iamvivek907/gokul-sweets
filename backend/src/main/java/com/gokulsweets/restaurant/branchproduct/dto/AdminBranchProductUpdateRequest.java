package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Immutable admin branch product update request data contract.
 *
 * @param available the available
 * @param priceOverride the price override
 * @param clearPriceOverride the clear price override
 * @param displayOrder the display order
 */
public record AdminBranchProductUpdateRequest(
        Boolean available,
        @DecimalMin(
                        value = "0.01",
                        inclusive = true,
                        message = "Price override must be greater than zero.")
                BigDecimal priceOverride,
        Boolean clearPriceOverride,
        @Min(value = 0, message = "Display order cannot be negative.") Integer displayOrder) {

    /**
     * Reports whether price update valid.
     *
     * @return the is price update valid result
     */
    @AssertTrue(message = "priceOverride and clearPriceOverride cannot be used together.")
    public boolean isPriceUpdateValid() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchProductUpdateRequest.class, "isPriceUpdateValid()");
        try {
            return !(priceOverride != null && Boolean.TRUE.equals(clearPriceOverride));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchProductUpdateRequest.class,
                    "isPriceUpdateValid()");
        }
    }
}
