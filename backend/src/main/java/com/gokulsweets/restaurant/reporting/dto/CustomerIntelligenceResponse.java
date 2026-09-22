package com.gokulsweets.restaurant.reporting.dto;

public record CustomerIntelligenceResponse(

        CustomerIntelligenceSummaryResponse summary,

        CustomerIntelligencePageResponse customers
) {
}
