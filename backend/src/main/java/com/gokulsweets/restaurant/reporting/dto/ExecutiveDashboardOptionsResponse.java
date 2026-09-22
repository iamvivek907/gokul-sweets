package com.gokulsweets.restaurant.reporting.dto;

import java.util.List;

public record ExecutiveDashboardOptionsResponse(

        List<ReportBranchOptionResponse> branches
) {
}
