package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.service.AppConstant;
import com.gokulsweets.restaurant.printing.enums.KitchenAlarmSound;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Private shop-agent alarm snapshots; delivery does not complete or acknowledge orders. */
@Service
@RequiredArgsConstructor
public class NativeKitchenAlarmService {
    private final JdbcTemplate jdbc;
    private final ApplicationClock clock;
    private final PreparationWindowProperties windows;
    private final EnhancementProperties flags;
    private final StaffAuthorizationService authorization;
    private final PrintStationControlService stations;

    /** Saves a branch's sound without changing order state, test sequence or alarm mode. */
    @Transactional
    public Map<String, Object> sound(
            Long branchId, PrinterStation station, KitchenAlarmSound sound) {
        final long started =
                MethodTiming.start(
                        NativeKitchenAlarmService.class,
                        "sound(Long,PrinterStation,KitchenAlarmSound)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(branchId);
            if (station != PrinterStation.KITCHEN || sound == null)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Select a kitchen alarm sound.");
            if (jdbc.update(
                            "UPDATE print_station_controls SET alarm_sound_profile=? WHERE"
                                    + " branch_id=? AND station='KITCHEN'",
                            sound.name(),
                            branchId)
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Register the kitchen station first.");
            return stations.get(branchId, station);
        } finally {
            MethodTiming.finish(
                    started,
                    NativeKitchenAlarmService.class,
                    "sound(Long,PrinterStation,KitchenAlarmSound)");
        }
    }

    /** Sets alarm mode separately from printing, using branch management authorization. */
    @Transactional
    public Map<String, Object> mode(Long branchId, PrinterStation station, boolean enabled) {
        final long started =
                MethodTiming.start(
                        NativeKitchenAlarmService.class, "mode(Long,PrinterStation,boolean)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(branchId);
            if (station != PrinterStation.KITCHEN || !flags.isAdminPreparationBoard())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Enable the preparation board and select KITCHEN.");
            if (jdbc.update(
                            "UPDATE print_station_controls SET alarm_enabled=? WHERE branch_id=?"
                                    + " AND station='KITCHEN'",
                            enabled,
                            branchId)
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Register the kitchen station first.");
            return stations.get(branchId, station);
        } finally {
            MethodTiming.finish(
                    started, NativeKitchenAlarmService.class, "mode(Long,PrinterStation,boolean)");
        }
    }

    /**
     * Requests one expiring sound test; the caller's action receipt prevents duplicate requests.
     */
    @Transactional
    public Map<String, Object> test(Long branchId, PrinterStation station) {
        final long started =
                MethodTiming.start(NativeKitchenAlarmService.class, "test(Long,PrinterStation)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(branchId);
            if (station != PrinterStation.KITCHEN)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Select KITCHEN.");
            if (jdbc.update(
                            "UPDATE print_station_controls SET"
                                + " alarm_test_sequence=alarm_test_sequence+1,"
                                + " alarm_test_requested_at=CURRENT_TIMESTAMP WHERE branch_id=? AND"
                                + " station='KITCHEN'",
                            branchId)
                    != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return stations.get(branchId, station);
        } finally {
            MethodTiming.finish(
                    started, NativeKitchenAlarmService.class, "test(Long,PrinterStation)");
        }
    }

    /** Returns current due counts only to the registered private-key kitchen agent. */
    @Transactional
    public Map<String, Object> poll(
            Long branchId,
            PrinterStation station,
            String agentId,
            String status,
            long testReceipt,
            int soundVersion) {
        final long started =
                MethodTiming.start(
                        NativeKitchenAlarmService.class,
                        "poll(Long,PrinterStation,String,String,long,int)");
        try {
            if (station != PrinterStation.KITCHEN
                    || !List.of(
                                    "STARTING",
                                    "DISABLED",
                                    "IDLE",
                                    "RINGING",
                                    "OFFLINE",
                                    "ERROR",
                                    "TESTING")
                            .contains(status))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var rows =
                    jdbc.queryForList(
                            "SELECT alarm_enabled, alarm_sound_profile, CASE WHEN"
                                + " alarm_test_requested_at > CURRENT_TIMESTAMP - INTERVAL '60"
                                + " seconds' THEN alarm_test_sequence ELSE 0 END AS test_sequence"
                                + " FROM print_station_controls WHERE branch_id=? AND"
                                + " station='KITCHEN' AND agent_id=? FOR UPDATE",
                            branchId,
                            agentId);
            if (rows.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Kitchen agent is not registered.");
            boolean enabled =
                    flags.isAdminPreparationBoard()
                            && Boolean.TRUE.equals(rows.getFirst().get("alarm_enabled"));
            var now = Timestamp.valueOf(clock.now());
            long[] counts =
                    enabled
                            ? jdbc.queryForObject(
                                    AppConstant.KITCHEN_PLANNING_SERVICE_BASE
                                            + """
SELECT COUNT(*) FILTER(WHERE order_status='CONFIRMED' AND bucket IN ('ELIGIBLE','OVERDUE')),
       COUNT(*) FILTER(WHERE order_status='PREPARING' AND bucket='OVERDUE') FROM planned
""",
                                    (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)},
                                    windows.getDeliveryLeadMinutes(),
                                    windows.getPriorityLeadMinutes(),
                                    windows.getAdminOverrideLeadMinutes(),
                                    windows.getNormalLeadMinutes(),
                                    branchId,
                                    now,
                                    now)
                            : new long[] {0, 0};
            jdbc.update(
                    "UPDATE print_station_controls SET alarm_last_seen_at=CURRENT_TIMESTAMP,"
                        + " alarm_status=?, alarm_test_receipt=GREATEST(alarm_test_receipt,"
                        + " LEAST(?,alarm_test_sequence)), alarm_sound_version=? WHERE branch_id=?"
                        + " AND station='KITCHEN'",
                    status,
                    testReceipt,
                    soundVersion,
                    branchId);
            return Map.of(
                    "enabled",
                    enabled,
                    "needsPreparation",
                    counts[0],
                    "readyOverdue",
                    counts[1],
                    "testSequence",
                    rows.getFirst().get("test_sequence"),
                    "soundProfile",
                    rows.getFirst().get("alarm_sound_profile"));
        } finally {
            MethodTiming.finish(
                    started,
                    NativeKitchenAlarmService.class,
                    "poll(Long,PrinterStation,String,String,long,int)");
        }
    }
}
