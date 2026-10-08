package com.gokulsweets.restaurant.reporting.dto;

/**
 * Immutable report branch option response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 */
public record ReportBranchOptionResponse(Long id, String code, String name) {}
