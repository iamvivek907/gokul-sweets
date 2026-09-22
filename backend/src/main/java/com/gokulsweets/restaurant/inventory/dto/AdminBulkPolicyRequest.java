package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminBulkPolicyRequest(
        @NotEmpty(message = "Select at least one product.")
        List<Long> branchProductIds,

        @NotNull(message = "Inventory policy is required.")
        @Valid
        AdminInventoryPolicyRequest policy
) {
}
