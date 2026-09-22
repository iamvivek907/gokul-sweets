package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrintAgentClaimRequest(

        @NotNull(
                message = "Branch ID is required."
        )
        Long branchId,


        @NotBlank(
                message = "Agent ID is required."
        )
        @Size(
                max = 120,
                message = "Agent ID cannot exceed 120 characters."
        )
        String agentId,


        @NotNull(
                message = "Printer station is required."
        )
        PrinterStation station
) {
}