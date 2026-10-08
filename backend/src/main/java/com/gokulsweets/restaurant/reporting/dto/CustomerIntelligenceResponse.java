package com.gokulsweets.restaurant.reporting.dto;

/** Immutable customer intelligence response data contract. */
public record CustomerIntelligenceResponse(
        CustomerIntelligenceSummaryResponse summary, CustomerIntelligencePageResponse customers) {}
