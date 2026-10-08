package com.gokulsweets.restaurant.customer.dto;

import java.util.List;

/** Immutable customer directory response data contract. */
public record CustomerDirectoryResponse(
        List<CustomerDirectoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
