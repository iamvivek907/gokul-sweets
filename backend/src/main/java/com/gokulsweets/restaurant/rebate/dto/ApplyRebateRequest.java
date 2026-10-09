package com.gokulsweets.restaurant.rebate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable apply rebate request data contract.
 *
 * @param code the code
 */
public record ApplyRebateRequest(@NotBlank @Size(max = 50) String code) {}
