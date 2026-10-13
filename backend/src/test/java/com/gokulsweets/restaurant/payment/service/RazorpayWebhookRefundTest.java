package com.gokulsweets.restaurant.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayClient;
import com.gokulsweets.restaurant.payment.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

class RazorpayWebhookRefundTest {
    RazorpayClient client = mock(RazorpayClient.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    PaymentStatusService statuses = mock(PaymentStatusService.class);
    RazorpayWebhookService service =
            new RazorpayWebhookService(client, new ObjectMapper(), payments, events, statuses);
    Payment payment = new Payment();

    @BeforeEach
    void setup() {
        payment.setId(1L);
        payment.setAmount(new BigDecimal("200"));
        payment.setRefundAmount(new BigDecimal("100"));
        payment.setCurrency("INR");
        payment.setProviderRefundId("rfnd_expected");
        payment.setRefundReferenceId("refund-local");
        when(payments.findByProviderAndProviderPaymentId(PaymentProviderType.RAZORPAY, "pay_1"))
                .thenReturn(Optional.of(payment));
        when(events.claim(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    void event(String id, String amount, String currency, String receipt, String type) {
        String body =
                "{\"event\":\""
                        + type
                        + "\",\"payload\":{\"refund\":{\"entity\":{\"id\":\""
                        + id
                        + "\",\"payment_id\":\"pay_1\",\"amount\":"
                        + amount
                        + ",\"currency\":\""
                        + currency
                        + "\",\"receipt\":\""
                        + receipt
                        + "\"}}}}";
        service.process(body.getBytes(StandardCharsets.UTF_8), "signature", "event-1");
    }

    @Test
    void onlyMatchingRefundMayCompleteOrFailTheRecordedRequest() {
        event("rfnd_unrelated", "10000", "INR", "", "refund.processed");
        event("rfnd_unrelated", "10000", "INR", "", "refund.failed");
        verifyNoInteractions(statuses);
        event("rfnd_expected", "10000", "INR", "", "refund.processed");
        verify(statuses).markRefunded(1L, "rfnd_expected");
    }

    @Test
    void rejectsWrongAmountCurrencyAndMalformedSubunits() {
        for (String amount : java.util.List.of("5000", "10000.5", "null", "\"10000\"", "-1"))
            assertThatThrownBy(() -> event("rfnd_expected", amount, "INR", "", "refund.processed"))
                    .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> event("rfnd_expected", "10000", "USD", "", "refund.processed"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(statuses);
    }

    @Test
    void lostInitiationAcknowledgementNeedsMatchingReceiptBeforeRecovery() {
        payment.setProviderRefundId(null);
        event("rfnd_expected", "10000", "INR", "unrelated", "refund.processed");
        verifyNoInteractions(statuses);
        event("rfnd_expected", "10000", "INR", "refund-local", "refund.processed");
        verify(statuses).markRefunded(1L, "rfnd_expected");
    }

    @Test
    void duplicateAndBadSignatureNeverChangeRefundState() {
        when(events.claim(anyString(), anyString(), anyString(), anyString())).thenReturn(0);
        event("rfnd_expected", "10000", "INR", "", "refund.processed");
        verifyNoInteractions(statuses);
        doThrow(new IllegalArgumentException("bad signature"))
                .when(client)
                .verifyWebhookSignature(any(), anyString());
        assertThatThrownBy(() -> event("rfnd_expected", "10000", "INR", "", "refund.processed"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(statuses);
    }
}
