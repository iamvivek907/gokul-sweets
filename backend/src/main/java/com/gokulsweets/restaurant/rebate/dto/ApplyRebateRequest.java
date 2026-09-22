package com.gokulsweets.restaurant.rebate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyRebateRequest(

        @NotBlank
        @Size(max = 50)
        String code

) {
}