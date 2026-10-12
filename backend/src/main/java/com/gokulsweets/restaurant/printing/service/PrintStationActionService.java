package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Serializes action acceptance with reconciliation, including requests arriving after cancellation.
 */
@Service
@RequiredArgsConstructor
public class PrintStationActionService {
    private final JdbcTemplate jdbc;
    private final PrintStationControlService controls;
    private final StaffAuthorizationService authorization;

    /** Commits the receipt atomically with the action; an existing receipt never replays it. */
    @Transactional
    public Map<String, Object> perform(
            Long branchId,
            PrinterStation station,
            UUID requestId,
            Supplier<Map<String, Object>> action) {
        final long started =
                MethodTiming.start(
                        PrintStationActionService.class,
                        "perform(Long,PrinterStation,UUID,Supplier<Map<String,Object>>)");
        try {
            authorization.requireBranchAccess(branchId);
            // Older clients retain their existing behavior; new Admin clients always supply an ID.
            if (requestId == null) return action.get();
            int inserted =
                    jdbc.update(
                            "INSERT INTO"
                                + " print_station_action_receipts(branch_id,station,request_id,outcome)"
                                + " VALUES (?,?,?,'ACCEPTED') ON CONFLICT DO NOTHING",
                            branchId,
                            station.name(),
                            requestId);
            return receipt(
                    branchId,
                    station,
                    requestId,
                    inserted == 1 ? action.get() : controls.get(branchId, station));
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationActionService.class,
                    "perform(Long,PrinterStation,UUID,Supplier<Map<String,Object>>)");
        }
    }

    /**
     * Waits for an in-flight transaction or durably cancels a request that has not been accepted.
     */
    @Transactional
    public Map<String, Object> reconcile(Long branchId, PrinterStation station, UUID requestId) {
        final long started =
                MethodTiming.start(
                        PrintStationActionService.class, "reconcile(Long,PrinterStation,UUID)");
        try {
            controls.get(branchId, station); // Authorize before creating a cancellation tombstone.
            // The unique-key conflict waits for a concurrent perform transaction to commit/roll
            // back.
            jdbc.update(
                    "INSERT INTO"
                            + " print_station_action_receipts(branch_id,station,request_id,outcome)"
                            + " VALUES (?,?,?,'CANCELLED') ON CONFLICT DO NOTHING",
                    branchId,
                    station.name(),
                    requestId);
            return receipt(branchId, station, requestId, controls.get(branchId, station));
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationActionService.class,
                    "reconcile(Long,PrinterStation,UUID)");
        }
    }

    /**
     * Attaches the durable outcome for this exact request, even after its mailbox command finishes.
     */
    private Map<String, Object> receipt(
            Long branchId, PrinterStation station, UUID requestId, Map<String, Object> state) {
        final long started =
                MethodTiming.start(
                        PrintStationActionService.class,
                        "receipt(Long,PrinterStation,UUID,Map<String,Object>)");
        try {
            var result = new HashMap<>(state);
            var outcome =
                    jdbc.queryForObject(
                            "SELECT outcome FROM print_station_action_receipts WHERE branch_id=?"
                                    + " AND station=? AND request_id=?",
                            String.class,
                            branchId,
                            station.name(),
                            requestId);
            result.put(
                    "actionReceipt", Map.of("requestId", requestId.toString(), "outcome", outcome));
            return result;
        } finally {
            MethodTiming.finish(
                    started,
                    PrintStationActionService.class,
                    "receipt(Long,PrinterStation,UUID,Map<String,Object>)");
        }
    }
}
