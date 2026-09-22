package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;
import com.gokulsweets.restaurant.rebate.RebateVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

        LocalDateTime updatedAt

) {
}
