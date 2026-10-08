package com.gokulsweets.restaurant.reporting.dto;

import java.util.List;

/** Immutable executive dashboard options response data contract. */
public record ExecutiveDashboardOptionsResponse(List<ReportBranchOptionResponse> branches) {}
