package com.gokulsweets.restaurant.printing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.delivery.DeliveryOrderWindowLookup;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.repository.KotRepository;
import com.gokulsweets.restaurant.printing.controller.PrintAgentController;
import com.gokulsweets.restaurant.printing.dto.PrintAgentClaimRequest;
import com.gokulsweets.restaurant.printing.dto.PrintAgentFailedRequest;
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
    void lostFailureReplyCanBeRepeatedWithoutMovingRetryTime() {
        PrintJob job = job(PrintJobStatus.CLAIMED);
        Kot kot = new Kot();
        kot.setId(9L);
        job.setKot(kot);
        job.setClaimedByAgent("shop");
        job.setClaimToken("claim-1");
        job.setAttemptCount(1);
        when(jobs.findForAcknowledgement(12L)).thenReturn(Optional.of(job));
        var request =
                new PrintAgentFailedRequest(
                        "shop", "claim-1", "OPERATOR_APPROVED_RETRY", "Checked paper");
        service.markFailed(12L, request);
        var retryAt = job.getNextAttemptAt();
        var failedAt = job.getFailedAt();
        assertThat(job.getClaimToken()).isNull();
        assertThat(job.getFailedClaimToken()).isEqualTo("claim-1");
        service.markFailed(12L, request);
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.FAILED);
        assertThat(job.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(job.getFailedAt()).isEqualTo(failedAt);
        verify(jobs, times(1)).saveAndFlush(job);
    }

    @Test
    void oldFailureReceiptCannotAlterANewerClaim() {
        PrintJob job = job(PrintJobStatus.CLAIMED);
        job.setClaimedByAgent("shop");
        job.setClaimToken("claim-2");
        job.setFailedClaimAgent("shop");
        job.setFailedClaimToken("claim-1");
        when(jobs.findForAcknowledgement(12L)).thenReturn(Optional.of(job));
        service.markFailed(
                12L,
                new PrintAgentFailedRequest(
                        "shop", "claim-1", "OPERATOR_APPROVED_RETRY", "Checked paper"));
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.CLAIMED);
        assertThat(job.getClaimToken()).isEqualTo("claim-2");
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void wrongAgentOrTokenCannotUseFailureReceipt() {
        PrintJob job = job(PrintJobStatus.FAILED);
        job.setFailedClaimAgent("shop");
        job.setFailedClaimToken("claim-1");
        when(jobs.findForAcknowledgement(12L)).thenReturn(Optional.of(job));
        for (var request :
                new PrintAgentFailedRequest[] {
                    new PrintAgentFailedRequest(
                            "other", "claim-1", "OPERATOR_APPROVED_RETRY", "Checked paper"),
                    new PrintAgentFailedRequest(
                            "shop", "wrong-token", "OPERATOR_APPROVED_RETRY", "Checked paper")
                }) {
            assertThatThrownBy(() -> service.markFailed(12L, request))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        }
        verify(jobs, never()).saveAndFlush(any());
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
