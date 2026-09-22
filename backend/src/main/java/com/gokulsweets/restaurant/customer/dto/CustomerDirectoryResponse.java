package com.gokulsweets.restaurant.customer.dto;

import java.util.List;

public record CustomerDirectoryResponse(

        List<CustomerDirectoryItemResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages
) {
}
