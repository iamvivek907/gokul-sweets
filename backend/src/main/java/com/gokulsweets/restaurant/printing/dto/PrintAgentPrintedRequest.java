package com.gokulsweets.restaurant.printing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrintAgentPrintedRequest(

        @NotBlank(
                message = "Agent ID is required."
        )
        @Size(
                max = 120,
                message = "Agent ID cannot exceed 120 characters."
        )
        String agentId,


        @NotBlank(
                message = "Claim token is required."
        )
        @Size(
                max = 100,
                message = "Claim token cannot exceed 100 characters."
        )
        String claimToken
) {
}