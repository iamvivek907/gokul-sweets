package com.gokulsweets.restaurant.reporting.dto;

/**
 * Immutable customer intelligence response data contract.
 *
 * @param summary the summary
 * @param customers the customers
 */
public record CustomerIntelligenceResponse(
        CustomerIntelligenceSummaryResponse summary, CustomerIntelligencePageResponse customers) {}
