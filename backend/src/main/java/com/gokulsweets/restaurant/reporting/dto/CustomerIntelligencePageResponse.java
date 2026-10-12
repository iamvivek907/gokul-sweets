package com.gokulsweets.restaurant.reporting.dto;

import java.util.List;

/**
 * Immutable customer intelligence page response data contract.
 *
 * @param content the content
 * @param page the page
 * @param size the size
 * @param totalElements the total elements
 * @param totalPages the total pages
 */
public record CustomerIntelligencePageResponse(
        List<CustomerIntelligenceItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
