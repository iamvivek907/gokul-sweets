package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

public record RebateSlabResponse(

        BigDecimal minimumOrderAmount,

        BigDecimal rebateAmount

) {
}