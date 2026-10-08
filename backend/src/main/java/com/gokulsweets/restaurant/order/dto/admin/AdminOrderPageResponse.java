package com.gokulsweets.restaurant.order.dto.admin;

import java.util.List;

/** Immutable admin order page response data contract. */
public record AdminOrderPageResponse(
        List<AdminOrderSummaryResponse> orders,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
