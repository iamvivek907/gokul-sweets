package com.gokulsweets.restaurant.reporting.dto;

import java.util.List;

/**
 * Immutable executive dashboard options response data contract.
 *
 * @param branches the branches
 */
public record ExecutiveDashboardOptionsResponse(List<ReportBranchOptionResponse> branches) {}
