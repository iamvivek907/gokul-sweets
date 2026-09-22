package com.gokulsweets.restaurant.customer.dto;

import java.util.List;

public record CustomerOrderHistoryResponse(

        List<CustomerOrderHistoryItemResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages
) {
}
