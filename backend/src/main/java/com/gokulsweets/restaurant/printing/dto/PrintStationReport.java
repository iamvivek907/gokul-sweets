package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import jakarta.validation.constraints.*;

import java.util.Map;

/** Agent identity and bounded runtime observations; never includes credentials or claim tokens. */
public record PrintStationReport(
        @NotNull @Positive Long branchId,
        @NotNull PrinterStation station,
        @NotBlank @Size(max = 120) String agentId,
        @NotNull Map<String, Object> runtime,
        String completedCommandId,
        Map<String, Object> commandResult) {}
