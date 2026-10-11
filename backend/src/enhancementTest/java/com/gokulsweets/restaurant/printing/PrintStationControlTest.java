package com.gokulsweets.restaurant.printing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.printing.controller.PrintStationControlController;
import com.gokulsweets.restaurant.printing.dto.*;
import com.gokulsweets.restaurant.printing.enums.*;
import com.gokulsweets.restaurant.printing.service.*;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@SpringBootTest
@Transactional
class PrintStationControlTest {
    @Autowired PrintStationControlService service;
    @Autowired NativeKitchenAlarmService alarms;
    @Autowired com.gokulsweets.restaurant.config.EnhancementProperties flags;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService authorization;
    Long branch;
    PrinterSetupRequest profile;

    @BeforeEach
    void setup() {
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        "PRINT-" + UUID.randomUUID(),
                        "Printer test branch");
        profile =
                new PrinterSetupRequest(
                        branch,
                        PrinterStation.KITCHEN,
                        "shop-test",
                        "KITCHEN_TEST",
                        PrinterProtocol.ESC_POS_USB,
                        "Test Windows Queue",
                        9100,
                        9600,
                        80,
                        false);
    }

    @Test
    void nativeAlarmHasIndependentModeHealthAndScopedCounts() {
        boolean previous = flags.isAdminPreparationBoard();
        flags.setAdminPreparationBoard(true);
        try {
            service.save(profile);
            assertThat(alarms.poll(branch, PrinterStation.KITCHEN, "shop-test", "STARTING", 0))
                    .containsEntry("enabled", false);
            assertThatThrownBy(
                            () ->
                                    alarms.poll(
                                            branch,
                                            PrinterStation.KITCHEN,
                                            "another-device",
                                            "IDLE",
                                            0))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(
                            () ->
                                    alarms.poll(
                                            branch, PrinterStation.BILLING, "shop-test", "IDLE", 0))
                    .isInstanceOf(ResponseStatusException.class);
            alarms.mode(branch, PrinterStation.KITCHEN, true);
            assertThat(service.get(branch, PrinterStation.KITCHEN)).containsEntry("enabled", false);
            long slot =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                                + " VALUES (?,CURRENT_DATE-1,'09:00','09:30',10) RETURNING id",
                            Long.class,
                            branch);
            for (String status : List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP")) {
                jdbc.update(
                        "INSERT INTO"
                            + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at)"
                            + " VALUES (?,?,?,'Test','9876543210','NORMAL',?,CURRENT_TIMESTAMP)",
                        "ALARM-" + UUID.randomUUID(),
                        branch,
                        slot,
                        status);
            }
            assertThat(alarms.poll(branch, PrinterStation.KITCHEN, "shop-test", "RINGING", 0))
                    .containsEntry("needsPreparation", 1L)
                    .containsEntry("readyOverdue", 1L);
            jdbc.update(
                    "UPDATE orders SET order_status='READY_FOR_PICKUP' WHERE branch_id=?", branch);
            assertThat(alarms.poll(branch, PrinterStation.KITCHEN, "shop-test", "RINGING", 0))
                    .containsEntry("needsPreparation", 0L)
                    .containsEntry("readyOverdue", 0L);
            alarms.test(branch, PrinterStation.KITCHEN);
            assertThat(alarms.poll(branch, PrinterStation.KITCHEN, "shop-test", "TESTING", 1))
                    .containsEntry("testSequence", 1L);
            assertThat(
                            ((Map<?, ?>) service.get(branch, PrinterStation.KITCHEN).get("alarm"))
                                    .get("lastSeenAt"))
                    .isNotNull();
            jdbc.update(
                    "UPDATE print_station_controls SET"
                            + " alarm_last_seen_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE"
                            + " branch_id=?",
                    branch);
            assertThat(
                            ((Map<?, ?>) service.get(branch, PrinterStation.KITCHEN).get("alarm"))
                                    .get("online"))
                    .isEqualTo(false);
            alarms.mode(branch, PrinterStation.KITCHEN, false);
            assertThat(alarms.poll(branch, PrinterStation.KITCHEN, "shop-test", "DISABLED", 1))
                    .containsEntry("enabled", false);
        } finally {
            flags.setAdminPreparationBoard(previous);
        }
    }

    @Test
    void nativeAlarmSettingsRequireBranchManagementAndNeverAcceptAnInvalidKey() {
        service.save(profile);
        doThrow(new AccessDeniedException("Forbidden"))
                .when(authorization)
                .requirePermission(PermissionName.BRANCH_MANAGE);
        assertThatThrownBy(() -> alarms.mode(branch, PrinterStation.KITCHEN, true))
                .isInstanceOf(AccessDeniedException.class);
        var key = mock(PrintAgentAuthenticationService.class);
        var nativeAlarm = mock(NativeKitchenAlarmService.class);
        doThrow(new AccessDeniedException("Invalid key")).when(key).authenticate("bad");
        var controller =
                new PrintStationControlController(
                        service, mock(PrintStationActionService.class), key, nativeAlarm);
        assertThatThrownBy(
                        () ->
                                controller.alarm(
                                        "bad",
                                        new PrintStationControlController.AlarmReport(
                                                branch,
                                                PrinterStation.KITCHEN,
                                                "shop-test",
                                                "IDLE",
                                                0)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(nativeAlarm);
    }

    @Test
    void registeringStartsPausedAndModeIsEnforcedForOnlyTheInstalledIdentity() {
        assertThat(service.save(profile)).containsEntry("enabled", false);
        assertThat(service.allowsClaim(branch, PrinterStation.KITCHEN, "shop-test")).isFalse();
        service.setEnabled(branch, PrinterStation.KITCHEN, true);
        assertThat(service.allowsClaim(branch, PrinterStation.KITCHEN, "shop-test")).isTrue();
        assertThat(service.allowsClaim(branch, PrinterStation.KITCHEN, "other-agent")).isFalse();
        service.setEnabled(branch, PrinterStation.KITCHEN, false);
        assertThat(service.allowsClaim(branch, PrinterStation.KITCHEN, "shop-test")).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM printer_devices WHERE branch_id=? AND active",
                                Long.class,
                                branch))
                .isEqualTo(1);
    }

    @Test
    void legacyStationWithoutManagedControlKeepsItsExistingClaimBehavior() {
        assertThat(service.allowsClaim(branch, PrinterStation.KITCHEN, "legacy")).isTrue();
    }

    @Test
    void unauthorizedBranchAccessIsRejectedBeforeAnyConfigurationWrite() {
        doThrow(new AccessDeniedException("Other branch"))
                .when(authorization)
                .requireBranchAccess(branch);
        assertThatThrownBy(() -> service.save(profile)).isInstanceOf(AccessDeniedException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM print_station_controls WHERE branch_id=?",
                                Long.class,
                                branch))
                .isZero();
        verify(authorization).requirePermission(PermissionName.BRANCH_MANAGE);
    }

    @Test
    void pendingRecoveryRequiresFreshOnlineReportAndExactJobAndPausedMode() {
        service.save(profile);
        assertThatThrownBy(() -> service.command(branch, PrinterStation.KITCHEN, "TEST", null))
                .isInstanceOf(ResponseStatusException.class);
        service.report(
                new PrintStationReport(
                        branch,
                        PrinterStation.KITCHEN,
                        "shop-test",
                        Map.of("pendingJobId", 12, "status", "NEEDS_ATTENTION"),
                        null,
                        null));
        assertThatThrownBy(() -> service.command(branch, PrinterStation.KITCHEN, "PRINTED", 99L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.command(branch, PrinterStation.KITCHEN, "TEST", null))
                .isInstanceOf(ResponseStatusException.class);
        service.setEnabled(branch, PrinterStation.KITCHEN, true);
        assertThatThrownBy(() -> service.command(branch, PrinterStation.KITCHEN, "PRINTED", 12L))
                .isInstanceOf(ResponseStatusException.class);
        service.setEnabled(branch, PrinterStation.KITCHEN, false);
        var result = service.command(branch, PrinterStation.KITCHEN, "PRINTED", 12L);
        assertThat(((Map<?, ?>) result.get("command")).get("id")).isNotNull();
    }

    @Test
    void commandAcknowledgementIsIdempotentAndDoesNotEraseAnotherCommand() {
        service.save(profile);
        service.report(
                new PrintStationReport(
                        branch,
                        PrinterStation.KITCHEN,
                        "shop-test",
                        Map.of("status", "READY"),
                        null,
                        null));
        var first = service.command(branch, PrinterStation.KITCHEN, "TEST", null);
        String firstId = (String) ((Map<?, ?>) first.get("command")).get("id");
        assertThatThrownBy(() -> service.command(branch, PrinterStation.KITCHEN, "TEST", null))
                .isInstanceOf(ResponseStatusException.class);
        var reply =
                new PrintStationReport(
                        branch,
                        PrinterStation.KITCHEN,
                        "shop-test",
                        Map.of("status", "READY"),
                        firstId,
                        Map.of("status", "DONE"));
        assertThat((Map<?, ?>) service.report(reply).get("command")).isEmpty();
        var second = service.command(branch, PrinterStation.KITCHEN, "TEST", null);
        service.report(reply); // Lost response from previous completed command.
        assertThat(service.get(branch, PrinterStation.KITCHEN).get("command"))
                .isEqualTo(second.get("command"));
    }

    @Test
    void wrongAgentAndOversizedReportsCannotReadOrUpdateMailbox() {
        service.save(profile);
        assertThatThrownBy(
                        () ->
                                service.report(
                                        new PrintStationReport(
                                                branch,
                                                PrinterStation.KITCHEN,
                                                "other",
                                                Map.of(),
                                                null,
                                                null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                service.report(
                                        new PrintStationReport(
                                                branch,
                                                PrinterStation.KITCHEN,
                                                "shop-test",
                                                Map.of("data", "x".repeat(8100)),
                                                null,
                                                null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.get(branch, PrinterStation.KITCHEN)).containsEntry("online", false);
    }

    @Test
    void runningOrPendingStationCannotBeReconfigured() {
        service.save(profile);
        service.setEnabled(branch, PrinterStation.KITCHEN, true);
        assertThatThrownBy(() -> service.save(profile)).isInstanceOf(ResponseStatusException.class);
        service.setEnabled(branch, PrinterStation.KITCHEN, false);
        service.report(
                new PrintStationReport(
                        branch,
                        PrinterStation.KITCHEN,
                        "shop-test",
                        Map.of("pendingJobId", 12),
                        null,
                        null));
        assertThatThrownBy(() -> service.save(profile)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void invalidPrivateKeyIsRejectedBeforeReadingControl() {
        var auth = mock(PrintAgentAuthenticationService.class);
        var controls = mock(PrintStationControlService.class);
        doThrow(new AccessDeniedException("Invalid key")).when(auth).authenticate("bad");
        var controller =
                new PrintStationControlController(
                        controls,
                        mock(PrintStationActionService.class),
                        auth,
                        mock(NativeKitchenAlarmService.class));
        assertThatThrownBy(
                        () ->
                                controller.report(
                                        "bad",
                                        new PrintStationReport(
                                                branch,
                                                PrinterStation.KITCHEN,
                                                "shop-test",
                                                Map.of(),
                                                null,
                                                null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(controls);
    }
}
