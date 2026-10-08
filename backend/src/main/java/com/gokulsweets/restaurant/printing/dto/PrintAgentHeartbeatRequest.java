package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Immutable print agent heartbeat request data contract. */
public record PrintAgentHeartbeatRequest(
        @NotNull Long branchId,
        @NotBlank @Size(max = 120) String agentId,
        @NotNull PrinterStation station) {}
