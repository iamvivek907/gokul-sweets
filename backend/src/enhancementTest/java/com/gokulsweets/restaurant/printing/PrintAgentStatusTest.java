package com.gokulsweets.restaurant.printing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.delivery.DeliveryOrderWindowLookup;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.printing.controller.PrintAgentController;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimRequest;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.repository.*;
import com.gokulsweets.restaurant.printing.service.PrintAgentAuthenticationService;
import com.gokulsweets.restaurant.printing.service.PrintAgentService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

class PrintAgentStatusTest {
    private final PrintJobRepository jobs = mock(PrintJobRepository.class);
    private final PrintAgentService service =
            new PrintAgentService(
                    mock(PrintAgentHeartbeatRepository.class),
                    mock(PrintJobClaimRepository.class),
                    jobs,
                    mock(PrinterDeviceRepository.class),
                    mock(KotRepository.class),
                    mock(DeliveryOrderWindowLookup.class),
                    mock(
                            com.gokulsweets.restaurant.printing.service.PrintStationControlService
                                    .class));

    private PrintJob job(PrintJobStatus status) {
        Branch branch = new Branch();
        branch.setId(1L);
        PrintJob job = new PrintJob();
        job.setId(12L);
        job.setBranch(branch);
        job.setStation(PrinterStation.KITCHEN);
        job.setStatus(status);
        when(jobs.findById(12L)).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void terminalStatusCanBeReadAfterClaimWasClearedWithoutUpdatingAnything() {
        PrintJob job = job(PrintJobStatus.PRINTED);
        var response =
                service.getJobStatus(
                        12L, new PrintAgentClaimRequest(1L, "shop", PrinterStation.KITCHEN));
        assertThat(response.status()).isEqualTo(PrintJobStatus.PRINTED);
        assertThat(response.printJobId()).isEqualTo(12L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(job.getClaimToken()).isNull();
        verify(jobs).findById(12L);
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void otherBranchAndStationAreNotDisclosed() {
        job(PrintJobStatus.PRINTED);
        for (var request :
                new PrintAgentClaimRequest[] {
                    new PrintAgentClaimRequest(2L, "shop", PrinterStation.KITCHEN),
                    new PrintAgentClaimRequest(1L, "shop", PrinterStation.BILLING)
                }) {
            assertThatThrownBy(() -> service.getJobStatus(12L, request))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        }
    }

    @Test
    void nonTerminalStatusIsReturnedWithoutAcknowledgingIt() {
        PrintJob job = job(PrintJobStatus.CLAIMED);
        var response =
                service.getJobStatus(
                        12L, new PrintAgentClaimRequest(1L, "shop", PrinterStation.KITCHEN));
        assertThat(response.status()).isEqualTo(PrintJobStatus.CLAIMED);
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.CLAIMED);
        verify(jobs).findById(12L);
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void missingJobReturnsNotFound() {
        when(jobs.findById(12L)).thenReturn(Optional.empty());
        assertThatThrownBy(
                        () ->
                                service.getJobStatus(
                                        12L,
                                        new PrintAgentClaimRequest(
                                                1L, "shop", PrinterStation.KITCHEN)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void invalidAgentKeyCannotReadStatus() {
        var auth = mock(PrintAgentAuthenticationService.class);
        var worker = mock(PrintAgentService.class);
        var controller = new PrintAgentController(auth, worker);
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid key"))
                .when(auth)
                .authenticate("invalid");
        assertThatThrownBy(
                        () ->
                                controller.status(
                                        "invalid",
                                        12L,
                                        new PrintAgentClaimRequest(
                                                1L, "shop", PrinterStation.KITCHEN)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(worker);
    }
}
