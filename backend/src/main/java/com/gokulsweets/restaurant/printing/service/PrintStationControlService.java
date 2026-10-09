package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.*;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Registers printers and coordinates durable, scoped operator controls for managed agents. */
@Service
@RequiredArgsConstructor
public class PrintStationControlService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StaffAuthorizationService authorization;

    /** Reads non-secret configuration and current agent status after branch authorization. */
    @Transactional(readOnly = true)
    public Map<String, Object> get(Long branchId, PrinterStation station) {
        final long started =
                MethodTiming.start(PrintStationControlService.class, "get(Long,PrinterStation)");
        try {
            authorization.requirePermission(PermissionName.ORDER_VIEW);
            authorization.requireBranchAccess(branchId);
            return snapshot(branchId, station);
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlService.class, "get(Long,PrinterStation)");
        }
    }

    /** Registers a printer while paused; serializes changes with claims and other operators. */
    @Transactional
    public Map<String, Object> save(PrinterSetupRequest profile) {
        final long started =
                MethodTiming.start(PrintStationControlService.class, "save(PrinterSetupRequest)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(profile.branchId());
            if (!List.of(58, 80).contains(profile.paperWidthMm())
                    || !List.of(9600, 19200, 38400, 57600, 115200).contains(profile.baudRate())
                    || profile.agentId().chars().anyMatch(Character::isISOControl)
                    || profile.target().chars().anyMatch(Character::isISOControl)
                    || profile.printerCode().chars().anyMatch(Character::isISOControl)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid printer profile.");
            }
            if (jdbc.queryForList(
                            "SELECT id FROM branches WHERE id=? FOR UPDATE", profile.branchId())
                    .isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
            var codes =
                    jdbc.queryForList(
                            "SELECT code,station FROM printer_devices WHERE branch_id=? AND"
                                    + " lower(code)=lower(?)",
                            profile.branchId(),
                            profile.printerCode());
            if (codes.stream()
                    .anyMatch(
                            row ->
                                    !profile.station().name().equals(row.get("station"))
                                            || !profile.printerCode().equals(row.get("code"))))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Printer code belongs to another station or uses different"
                                + " capitalization.");
            var current =
                    jdbc.queryForList(
                            "SELECT * FROM print_station_controls WHERE branch_id=? AND station=?"
                                    + " FOR UPDATE",
                            profile.branchId(),
                            profile.station().name());
            if (!current.isEmpty()) {
                var row = current.getFirst();
                var previous = decode(row.get("profile"));
                if (!profile.agentId().equals(row.get("agent_id"))
                        || !profile.printerCode().equals(previous.get("printerCode"))) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Keep the installed agent ID and printer code. Remove the old"
                                    + " installation before changing identity.");
                }
                if (Boolean.TRUE.equals(row.get("enabled"))
                        || row.get("command_id") != null
                        || decode(row.get("runtime")).get("pendingJobId") != null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Pause printing and resolve pending work before changing the printer.");
                }
            }
            Long claimed =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM print_jobs WHERE branch_id=? AND station=? AND"
                                    + " status='CLAIMED'",
                            Long.class,
                            profile.branchId(),
                            profile.station().name());
            if (claimed != null && claimed > 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Finish or reconcile the claimed ticket first.");
            jdbc.update(
                    "UPDATE printer_devices SET active=FALSE, updated_at=CURRENT_TIMESTAMP WHERE"
                            + " branch_id=? AND station=?",
                    profile.branchId(),
                    profile.station().name());
            jdbc.update(
                    """
INSERT INTO printer_devices(branch_id,code,name,station,protocol,host,port,paper_width_mm,auto_cut,active)
VALUES (?,?,?,?,?,?,?,?,?,TRUE)
ON CONFLICT (branch_id,code) DO UPDATE SET name=EXCLUDED.name,station=EXCLUDED.station,
 protocol=EXCLUDED.protocol,host=EXCLUDED.host,port=EXCLUDED.port,paper_width_mm=EXCLUDED.paper_width_mm,
 auto_cut=EXCLUDED.auto_cut,active=TRUE,updated_at=CURRENT_TIMESTAMP
""",
                    profile.branchId(),
                    profile.printerCode(),
                    profile.printerCode(),
                    profile.station().name(),
                    profile.protocol().name(),
                    profile.target(),
                    profile.port(),
                    profile.paperWidthMm(),
                    profile.autoCut());
            jdbc.update(
                    """
INSERT INTO print_station_controls(branch_id,station,agent_id,profile) VALUES (?,?,?,?)
ON CONFLICT(branch_id,station) DO UPDATE SET profile=EXCLUDED.profile
""",
                    profile.branchId(),
                    profile.station().name(),
                    profile.agentId(),
                    mapper.writeValueAsString(profile));
            return snapshot(profile.branchId(), profile.station());
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlService.class, "save(PrinterSetupRequest)");
        }
    }

    /** Starts or pauses future claims; never cancels a ticket already in flight. */
    @Transactional
    public Map<String, Object> setEnabled(Long branchId, PrinterStation station, boolean enabled) {
        final long started =
                MethodTiming.start(
                        PrintStationControlService.class,
                        "setEnabled(Long,PrinterStation,boolean)");
        try {
            authorization.requirePermission(PermissionName.ORDER_START_PREPARATION);
            authorization.requireBranchAccess(branchId);
            if (jdbc.update(
                            "UPDATE print_station_controls SET enabled=? WHERE branch_id=? AND"
                                    + " station=?",
                            enabled,
                            branchId,
                            station.name())
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Save a printer profile first.");
            return snapshot(branchId, station);
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationControlService.class,
                    "setEnabled(Long,PrinterStation,boolean)");
        }
    }

    /** Checks the station gate under a shared lock held through the claim transaction. */
    public boolean allowsClaim(Long branchId, PrinterStation station, String agentId) {
        final long started =
                MethodTiming.start(
                        PrintStationControlService.class,
                        "allowsClaim(Long,PrinterStation,String)");
        try {
            jdbc.queryForList("SELECT id FROM branches WHERE id=? FOR SHARE", branchId);
            var rows =
                    jdbc.queryForList(
                            "SELECT enabled,agent_id FROM print_station_controls WHERE branch_id=?"
                                    + " AND station=? FOR SHARE",
                            branchId,
                            station.name());
            return rows.isEmpty()
                    || (Boolean.TRUE.equals(rows.getFirst().get("enabled"))
                            && agentId.equals(rows.getFirst().get("agent_id")));
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationControlService.class,
                    "allowsClaim(Long,PrinterStation,String)");
        }
    }

    /**
     * Accepts runtime observations only from the configured identity; reconciles command replies
     * idempotently.
     */
    @Transactional
    public Map<String, Object> report(PrintStationReport report) {
        final long started =
                MethodTiming.start(PrintStationControlService.class, "report(PrintStationReport)");
        try {
            if (mapper.writeValueAsString(report.runtime()).length() > 8000
                    || (report.commandResult() != null
                            && mapper.writeValueAsString(report.commandResult()).length() > 2000))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Agent report exceeds size limit.");
            int count =
                    jdbc.update(
                            """
UPDATE print_station_controls SET runtime=?,last_seen_at=CURRENT_TIMESTAMP,
 command_result=CASE WHEN command_id=? THEN ? ELSE command_result END,
 command=CASE WHEN command_id=? THEN NULL ELSE command END,
 command_id=CASE WHEN command_id=? THEN NULL ELSE command_id END
WHERE branch_id=? AND station=? AND agent_id=?
""",
                            mapper.writeValueAsString(report.runtime()),
                            report.completedCommandId(),
                            mapper.writeValueAsString(report.commandResult()),
                            report.completedCommandId(),
                            report.completedCommandId(),
                            report.branchId(),
                            report.station().name(),
                            report.agentId());
            if (count != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Agent identity is not registered for this station.");
            return snapshot(report.branchId(), report.station());
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlService.class, "report(PrintStationReport)");
        }
    }

    /**
     * Queues one operator-approved test or recovery command against the observed pending ticket.
     */
    @Transactional
    public Map<String, Object> command(
            Long branchId, PrinterStation station, String action, Long jobId) {
        final long started =
                MethodTiming.start(
                        PrintStationControlService.class,
                        "command(Long,PrinterStation,String,Long)");
        try {
            authorization.requirePermission(PermissionName.ORDER_START_PREPARATION);
            authorization.requireBranchAccess(branchId);
            if (!List.of("TEST", "PRINTED", "RETRY").contains(action))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown printer action.");
            var rows =
                    jdbc.queryForList(
                            "SELECT *,last_seen_at > CURRENT_TIMESTAMP - INTERVAL '60 seconds' AS"
                                    + " online FROM print_station_controls WHERE branch_id=? AND"
                                    + " station=? FOR UPDATE",
                            branchId,
                            station.name());
            if (rows.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Save a printer profile first.");
            var row = rows.getFirst();
            var runtime = decode(row.get("runtime"));
            if (!Boolean.TRUE.equals(row.get("online")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The print station is offline. Sign in to Windows and check its"
                                + " connection.");
            if (row.get("command_id") != null)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Wait for the current printer action to finish.");
            Object pending = runtime.get("pendingJobId");
            if (action.equals("TEST")
                    ? pending != null
                    : jobId == null
                            || !(pending instanceof Number number)
                            || number.longValue() != jobId.longValue())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The pending ticket changed. Refresh and inspect the paper first.");
            if (Boolean.TRUE.equals(row.get("enabled")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Pause printing before a test or recovery.");
            String id = UUID.randomUUID().toString();
            Map<String, Object> command = new HashMap<>();
            command.put("id", id);
            command.put("action", action);
            command.put("expiresAt", System.currentTimeMillis() + 60_000);
            command.put("jobId", jobId);
            jdbc.update(
                    "UPDATE print_station_controls SET command_id=?,command=?,command_result=NULL"
                            + " WHERE branch_id=? AND station=?",
                    id,
                    mapper.writeValueAsString(command),
                    branchId,
                    station.name());
            return snapshot(branchId, station);
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationControlService.class,
                    "command(Long,PrinterStation,String,Long)");
        }
    }

    /**
     * Builds a non-secret station snapshot, preserving offline state independently from the
     * requested mode.
     */
    private Map<String, Object> snapshot(Long branchId, PrinterStation station) {
        final long started =
                MethodTiming.start(
                        PrintStationControlService.class, "snapshot(Long,PrinterStation)");
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT *,last_seen_at > CURRENT_TIMESTAMP - INTERVAL '60 seconds' AS"
                                    + " online FROM print_station_controls WHERE branch_id=? AND"
                                    + " station=?",
                            branchId,
                            station.name());
            if (rows.isEmpty()) return Map.of("configured", false);
            var row = rows.getFirst();
            Map<String, Object> result = new HashMap<>();
            result.put("configured", true);
            result.put("profile", decode(row.get("profile")));
            result.put("enabled", row.get("enabled"));
            result.put("online", Boolean.TRUE.equals(row.get("online")));
            result.put("runtime", decode(row.get("runtime")));
            result.put("command", decode(row.get("command")));
            result.put("commandResult", decode(row.get("command_result")));
            return result;
        } finally {
            MethodTiming.finish(
                    started, PrintStationControlService.class, "snapshot(Long,PrinterStation)");
        }
    }

    /** Decodes a bounded non-secret JSON document from the control mailbox. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(Object value) {
        final long started = MethodTiming.start(PrintStationControlService.class, "decode(Object)");
        try {
            if (value == null || "null".equals(value.toString())) return Map.of();
            return mapper.readValue(value.toString(), Map.class);
        } finally {
            MethodTiming.finish(started, PrintStationControlService.class, "decode(Object)");
        }
    }
}
