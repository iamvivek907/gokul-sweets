package com.gokulsweets.restaurant.order.dto.admin;

import java.util.List;

public record AdminOrderPageResponse(

        List<AdminOrderSummaryResponse> orders,

        int page,

        int size,

        long totalElements,

        int totalPages
) {
}