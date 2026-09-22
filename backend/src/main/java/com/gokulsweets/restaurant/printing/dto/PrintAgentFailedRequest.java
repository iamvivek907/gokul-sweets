package com.gokulsweets.restaurant.printing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrintAgentFailedRequest(

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
        String claimToken,


        @NotBlank(
                message = "Error code is required."
        )
        @Size(
                max = 100,
                message = "Error code cannot exceed 100 characters."
        )
        String errorCode,


        @Size(
                max = 500,
                message = "Error message cannot exceed 500 characters."
        )
        String errorMessage
) {
}