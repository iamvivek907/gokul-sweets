package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;
import com.gokulsweets.restaurant.rebate.RebateVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.gokulsweets.restaurant.rebate.dto.RebateSlabRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record CreateRebateRequest(

        @NotBlank
        @Size(max = 50)
        String code,

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @NotNull
        RebateScope scope,

        @NotNull
        RebateVisibility visibility,

        @NotNull
        RebateType rebateType,

        BigDecimal rebateValue,

        BigDecimal minimumOrderAmount,

        BigDecimal maximumDiscountAmount,

        @Min(1)
        Integer maxTotalUses,

        @Min(1)
        Integer maxUsesPerCustomer,

        Long branchId,

        @NotNull
        LocalDateTime validFrom,

        @NotNull
        LocalDateTime validUntil,

        Set<String> customerPhones,

        List<@Valid RebateSlabRequest> slabs

) {
}
