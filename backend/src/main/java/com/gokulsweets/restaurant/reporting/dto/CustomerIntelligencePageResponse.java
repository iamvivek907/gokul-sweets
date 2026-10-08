package com.gokulsweets.restaurant.reporting.dto;

import java.util.List;

/** Immutable customer intelligence page response data contract. */
public record CustomerIntelligencePageResponse(
        List<CustomerIntelligenceItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
