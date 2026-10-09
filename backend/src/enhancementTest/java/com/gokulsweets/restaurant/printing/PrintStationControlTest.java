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
                        controls, mock(PrintStationActionService.class), auth);
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
