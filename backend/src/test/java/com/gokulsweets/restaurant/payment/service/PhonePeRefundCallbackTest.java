package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.*;
import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;
import com.gokulsweets.restaurant.payment.provider.phonepe.*;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.occasion.OccasionCommitmentService;
import com.gokulsweets.restaurant.config.EnvironmentIsolationGuard;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhonePeRefundCallbackTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PhonePeProperties properties = new PhonePeProperties();
    final PaymentRepository repository = mock(PaymentRepository.class);
    final PaymentStatusService statuses = mock(PaymentStatusService.class);
    final OccasionCommitmentService occasions = mock(OccasionCommitmentService.class);
    final PhonePeCallbackService callbacks;
    final Payment payment = new Payment();

    PhonePeRefundCallbackTest() {
        properties.setWebhookChecksumKeyId("test-key");
        properties.setWebhookChecksumSecret("test-only-secret");
        var client = new PhonePeClient(properties, mapper);
        var provider = new PhonePePaymentProvider(client, properties, mock(EnvironmentIsolationGuard.class));
        callbacks = new PhonePeCallbackService(client, provider, mapper, repository, statuses, occasions);
        payment.setId(151L); payment.setProvider(PaymentProviderType.PHONEPE);
        payment.setProviderOrderId("DEV-order1"); payment.setRefundReferenceId("GKC-ref1");
        payment.setRefundAmount(new BigDecimal("100.00")); payment.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        when(repository.findByRefundReferenceId("GKC-ref1")).thenReturn(Optional.of(payment));
    }

    String body(String event, String state, String order, long amount) {
        return "{\"event\":\""+event+"\",\"payload\":{\"merchantRefundId\":\"GKC-ref1\","
                +"\"originalMerchantOrderId\":\""+order+"\",\"refundId\":\"refund1\",\"amount\":"+amount+",\"state\":\""+state+"\"}}";
    }
    void receive(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getWebhookChecksumSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        callbacks.process(raw, "test-key", HexFormat.of().formatHex(mac.doFinal(raw)));
    }

    @Test void authenticatedCompletionMapsRefundWithoutMerchantOrderIdPaymentField() throws Exception {
        receive(body("pg.refund.completed","COMPLETED","DEV-order1",10000));
        verify(statuses).markRefunded(151L,"refund1");
        verifyNoInteractions(occasions);
    }
    @Test void authenticatedFailureIsTerminal() throws Exception {
        receive(body("pg.refund.failed","FAILED","DEV-order1",10000));
        verify(statuses).markRefundFailed(eq(151L),eq("refund1"),contains("Staff review"));
    }
    @Test void mismatchedAmountOrderRefundIdOrEventNeverChangesPayment() {
        for (String body : List.of(body("pg.refund.completed","COMPLETED","DEV-order1",11700),
                body("pg.refund.completed","COMPLETED","other-order",10000),
                body("pg.refund.completed","FAILED","DEV-order1",10000))) {
            assertThatThrownBy(() -> receive(body)).isInstanceOf(RuntimeException.class);
        }
        payment.setProviderRefundId("different-refund");
        assertThatThrownBy(() -> receive(body("pg.refund.completed","COMPLETED","DEV-order1",10000)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(statuses);
    }
    @Test void invalidSignatureNeverLooksUpPayment() {
        assertThatThrownBy(() -> callbacks.process(body("pg.refund.completed","COMPLETED","DEV-order1",10000)
                .getBytes(StandardCharsets.UTF_8), "test-key", "bad-signature"))
                .isInstanceOf(PaymentSignatureException.class);
        verifyNoInteractions(repository, statuses);
    }
    @Test void unknownReferenceOrDifferentProviderCannotCompleteRefund() {
        payment.setProvider(PaymentProviderType.RAZORPAY);
        assertThatThrownBy(() -> receive(body("pg.refund.completed","COMPLETED","DEV-order1",10000)))
                .isInstanceOf(IllegalArgumentException.class);
        when(repository.findByRefundReferenceId("GKC-ref1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> receive(body("pg.refund.completed","COMPLETED","DEV-order1",10000)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(statuses);
    }
}
