package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

public record AppliedRebateResponse(

        String orderNumber,

        String rebateCode,

        String rebateName,

        BigDecimal rebateAmount,

        BigDecimal amountBeforeRebate,

        BigDecimal totalAmount

) {
}