package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.*;
import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundServiceTest {
    final PaymentRepository repository = mock(PaymentRepository.class);
    final PaymentStatusService statuses = mock(PaymentStatusService.class);
    final PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    final PaymentProvider provider = mock(PaymentProvider.class);
    final PaymentRefundService service = new PaymentRefundService(repository, statuses, registry);

    Payment pending(int failures) {
        var payment = new Payment();
        payment.setId(151L);
        payment.setProvider(PaymentProviderType.PHONEPE);
        payment.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        payment.setRefundCheckFailures(failures);
        when(repository.findByIdWithOrder(151L)).thenReturn(Optional.of(payment));
        when(repository.claimRefundCheck(eq(151L), any(), any())).thenReturn(1);
        when(repository.recordRefundSubmissionAttempt(eq(151L), any())).thenReturn(1);
        when(registry.require(PaymentProviderType.PHONEPE)).thenReturn(provider);
        return payment;
    }

    @Test void commitsSubmissionMarkerBeforeProviderHttpAndSchedulesPending() {
        pending(0);
        when(provider.refund(any())).thenReturn(new RefundResult(PaymentStatus.REFUND_PENDING, "refund1", null));
        service.processPendingRefund(151L);
        var ordered = inOrder(repository, provider, statuses);
        ordered.verify(repository).claimRefundCheck(eq(151L), any(), any());
        ordered.verify(repository).findByIdWithOrder(151L);
        ordered.verify(repository).recordRefundSubmissionAttempt(eq(151L), any());
        ordered.verify(provider).refund(any());
        ordered.verify(statuses).markRefundInitiated(151L, "refund1");
        verify(repository).finishRefundCheck(eq(151L), any(), any(), eq(0), eq(false), isNull(), any());
    }

    @Test void malformedResponseBacksOffWithoutDeclaringRefundFailedOrLeakingMessages() {
        pending(7);
        when(provider.refund(any())).thenThrow(new PaymentGatewayException(
                "PHONEPE_REFUND_INVALID_RESPONSE", "secret customer payload", true));
        service.processPendingRefund(151L);
        var lease = ArgumentCaptor.forClass(LocalDateTime.class);
        var next = ArgumentCaptor.forClass(LocalDateTime.class);
        var reason = ArgumentCaptor.forClass(String.class);
        verify(repository).finishRefundCheck(eq(151L), lease.capture(), next.capture(), eq(8), eq(true), reason.capture(), any());
        assertThat(next.getValue()).isAfter(lease.getValue().plusMinutes(39));
        assertThat(reason.getValue()).contains("Staff review required", "PHONEPE_REFUND_INVALID_RESPONSE")
                .doesNotContain("secret", "customer payload");
        verifyNoInteractions(statuses);
    }

    @Test void claimedOrNotYetDueRefundDoesNotCallProvider() {
        when(repository.claimRefundCheck(eq(151L), any(), any())).thenReturn(0);
        service.processPendingRefund(151L);
        verifyNoInteractions(provider, registry, statuses);
        verify(repository, never()).findByIdWithOrder(any());
    }

    @Test void acceptedSubmissionOnlyChecksStatus() {
        var payment = pending(3);
        payment.setRefundRequestedAt(LocalDateTime.now());
        when(provider.verifyRefund(payment)).thenReturn(new RefundResult(PaymentStatus.REFUNDED, "refund1", null));
        service.processPendingRefund(151L);
        verify(provider, never()).refund(any());
        verify(repository, never()).recordRefundSubmissionAttempt(any(), any());
        verify(statuses).markRefunded(151L, "refund1");
    }

    @Test void failureDoesNotPreventProcessingAnotherDueRefund() {
        var payment = pending(0);
        when(repository.findDueRefundIds(any(), any())).thenReturn(List.of(151L, 152L));
        when(provider.refund(payment)).thenThrow(new IllegalStateException("invalid response"));
        when(repository.claimRefundCheck(eq(152L), any(), any())).thenReturn(1);
        var second = new Payment(); second.setId(152L); second.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        second.setProvider(PaymentProviderType.PHONEPE); second.setRefundRequestedAt(LocalDateTime.now());
        when(repository.findByIdWithOrder(152L)).thenReturn(Optional.of(second));
        when(provider.verifyRefund(second)).thenReturn(new RefundResult(PaymentStatus.REFUNDED,"refund2",null));
        assertThat(service.processPendingRefunds()).isEqualTo(1);
        verify(statuses).markRefunded(152L, "refund2");
    }

    @Test void cancellationCompletedDuringClaimDoesNotSubmit() {
        pending(0);
        when(repository.recordRefundSubmissionAttempt(eq(151L), any())).thenReturn(0);
        service.processPendingRefund(151L);
        verifyNoInteractions(provider, statuses);
    }
}
