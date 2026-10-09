package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.*;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.service.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authenticated Admin controls and private-key agent mailbox, without browser-to-localhost access.
 */
@RestController
@RequiredArgsConstructor
public class PrintStationControlController {
    private final PrintStationControlService service;
    private final PrintAgentAuthenticationService authentication;

    /** Requested future printing mode. */
    public record Mode(@NotNull Boolean enabled) {}

    /** Test or recovery request; recovery requires the observed ticket ID. */
    public record Command(@NotNull String action, Long jobId) {}

    /** Reads an authorized branch station. */
    @GetMapping("/api/admin/printing/station")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public Map<String, Object> get(
            @RequestParam Long branchId,
            @RequestParam(defaultValue = "KITCHEN") PrinterStation station) {
        final long started =
                MethodTiming.start(PrintStationControlController.class, "get(Long,PrinterStation)");
        try {
            return service.get(branchId, station);
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlController.class, "get(Long,PrinterStation)");
        }
    }

    /** Registers a printer without requiring SQL. */
    @PostMapping("/api/admin/printing/station/profile")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public Map<String, Object> save(@Valid @RequestBody PrinterSetupRequest profile) {
        final long started =
                MethodTiming.start(
                        PrintStationControlController.class, "save(PrinterSetupRequest)");
        try {
            return service.save(profile);
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlController.class, "save(PrinterSetupRequest)");
        }
    }

    /** Sets the requested printing mode. */
    @PostMapping("/api/admin/printing/station/mode")
    @PreAuthorize("hasAuthority('ORDER_START_PREPARATION')")
    public Map<String, Object> mode(
            @RequestParam Long branchId,
            @RequestParam PrinterStation station,
            @Valid @RequestBody Mode mode) {
        final long started =
                MethodTiming.start(
                        PrintStationControlController.class, "mode(Long,PrinterStation,Mode)");
        try {
            return service.setEnabled(branchId, station, mode.enabled());
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlController.class, "mode(Long,PrinterStation,Mode)");
        }
    }

    /** Queues an explicit operator action. */
    @PostMapping("/api/admin/printing/station/command")
    @PreAuthorize("hasAuthority('ORDER_START_PREPARATION')")
    public Map<String, Object> command(
            @RequestParam Long branchId,
            @RequestParam PrinterStation station,
            @Valid @RequestBody Command command) {
        final long started =
                MethodTiming.start(
                        PrintStationControlController.class,
                        "command(Long,PrinterStation,Command)");
        try {
            return service.command(branchId, station, command.action(), command.jobId());
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationControlController.class,
                    "command(Long,PrinterStation,Command)");
        }
    }

    /** Authenticates the agent before accessing its scoped command mailbox. */
    @PostMapping("/api/print-agent/control")
    public Map<String, Object> report(
            @RequestHeader(value = "X-Print-Agent-Key", required = false) String key,
            @Valid @RequestBody PrintStationReport report) {
        final long started =
                MethodTiming.start(
                        PrintStationControlController.class, "report(String,PrintStationReport)");
        try {
            authentication.authenticate(key);
            return service.report(report);
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationControlController.class,
                    "report(String,PrintStationReport)");
        }
    }
}
