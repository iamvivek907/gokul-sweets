package com.gokulsweets.restaurant.printing.dto;

import com.gokulsweets.restaurant.printing.enums.*;

import jakarta.validation.constraints.*;

/** Non-secret connection profile registered by an authorized branch administrator. */
public record PrinterSetupRequest(
        @NotNull @Positive Long branchId,
        @NotNull PrinterStation station,
        @NotBlank @Size(max = 120) String agentId,
        @NotBlank @Size(max = 50) String printerCode,
        @NotNull PrinterProtocol protocol,
        @NotBlank @Size(max = 255) String target,
        @Min(1) @Max(65535) int port,
        int baudRate,
        int paperWidthMm,
        boolean autoCut) {}
