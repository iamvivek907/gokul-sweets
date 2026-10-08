package com.gokulsweets.restaurant.order.dto.admin;

import java.util.List;

/**
 * Immutable admin order page response data contract.
 *
 * @param orders the orders
 * @param page the page
 * @param size the size
 * @param totalElements the total elements
 * @param totalPages the total pages
 */
public record AdminOrderPageResponse(
        List<AdminOrderSummaryResponse> orders,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
