package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;
import com.gokulsweets.restaurant.rebate.RebateVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Immutable update rebate request data contract.
 *
 * @param code the code
 * @param name the name
 * @param description the description
 * @param scope the scope
 * @param visibility the visibility
 * @param rebateType the rebate type
 * @param rebateValue the rebate value
 * @param minimumOrderAmount the minimum order amount
 * @param maximumDiscountAmount the maximum discount amount
 * @param maxTotalUses the max total uses
 * @param maxUsesPerCustomer the max uses per customer
 * @param branchId the branch id
 * @param validFrom the valid from
 * @param validUntil the valid until
 * @param customerPhones the customer phones
 * @param slabs the slabs
 */
public record UpdateRebateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull RebateScope scope,
        @NotNull RebateVisibility visibility,
        @NotNull RebateType rebateType,
        BigDecimal rebateValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        @Min(1) Integer maxTotalUses,
        @Min(1) Integer maxUsesPerCustomer,
        Long branchId,
        @NotNull LocalDateTime validFrom,
        @NotNull LocalDateTime validUntil,
        Set<String> customerPhones,
        List<@Valid RebateSlabRequest> slabs) {}
