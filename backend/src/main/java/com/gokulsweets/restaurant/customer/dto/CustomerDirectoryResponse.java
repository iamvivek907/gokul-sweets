package com.gokulsweets.restaurant.customer.dto;

import java.util.List;

/**
 * Immutable customer directory response data contract.
 *
 * @param content the content
 * @param page the page
 * @param size the size
 * @param totalElements the total elements
 * @param totalPages the total pages
 */
public record CustomerDirectoryResponse(
        List<CustomerDirectoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
