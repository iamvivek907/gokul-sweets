package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;
import com.gokulsweets.restaurant.rebate.RebateVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Immutable rebate response data contract.
 *
 * @param id the id
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
 * @param branchName the branch name
 * @param validFrom the valid from
 * @param validUntil the valid until
 * @param active the active
 * @param slabs the slabs
 * @param customerPhones the customer phones
 * @param createdBy the created by
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record RebateResponse(
        Long id,
        String code,
        String name,
        String description,
        RebateScope scope,
        RebateVisibility visibility,
        RebateType rebateType,
        BigDecimal rebateValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        Integer maxTotalUses,
        Integer maxUsesPerCustomer,
        Long branchId,
        String branchName,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        boolean active,
        List<RebateSlabResponse> slabs,
        Set<String> customerPhones,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
