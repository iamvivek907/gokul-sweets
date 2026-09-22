package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.PreparationBatchResult;

public record AdminBatchPreparationItemResponse(

        String orderNumber,

        PreparationBatchResult result,

        String message

) {
}