package com.gokulsweets.restaurant.payment.provider.phonepe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PhonePeRefundTest {
  final PhonePeClient client = mock(PhonePeClient.class);
  final PhonePePaymentProvider provider =
      new PhonePePaymentProvider(
          client,
          new PhonePeProperties(),
          mock(com.gokulsweets.restaurant.config.EnvironmentIsolationGuard.class));

  Payment payment() {
    var p = new Payment();
    p.setProviderOrderId("DEV-payment-1");
    p.setAmount(new BigDecimal("117"));
    p.setRefundAmount(new BigDecimal("100"));
    p.setRefundReferenceId("GKC-123456");
    p.setRefundSubmissionAttemptedAt(java.time.LocalDateTime.now());
    return p;
  }

  @Test
  void newRefundSubmitsWithoutCheckingANonexistentRefund() {
    var p = payment();
    p.setRefundSubmissionAttemptedAt(null);
    when(client.refund(p.getRefundReferenceId(), p.getProviderOrderId(), p.getRefundAmount()))
        .thenReturn(new PhonePeClient.RefundResponse("refund1", 10000, "PENDING", null, null));
    assertThat(provider.refund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    verify(client, never()).refundStatus(anyString());
    verify(client).refund(p.getRefundReferenceId(), p.getProviderOrderId(), p.getRefundAmount());
  }

  @Test
  void malformedRecoveryResponseNeverSubmitsAgain() {
    var p = payment();
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenThrow(new PaymentGatewayException("PHONEPE_REFUND_INVALID_RESPONSE", "Malformed", true));
    assertThatThrownBy(() -> provider.refund(p)).isInstanceOf(PaymentGatewayException.class);
    verify(client, never()).refund(anyString(), anyString(), any());
  }

  @Test
  void lostInitiationResponseIsRecoveredWithoutAnotherPost() {
    var p = payment();
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenReturn(
            new PhonePeClient.RefundResponse(
                "refund1", 10000, "COMPLETED", null, p.getProviderOrderId()));
    assertThat(provider.refund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    verify(client, never()).refund(anyString(), anyString(), any());
    p.setProviderRefundId("refund1");
    assertThat(provider.verifyRefund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    when(client.refundStatus(p.getRefundReferenceId())).thenReturn(
        new PhonePeClient.RefundResponse("other-refund", 10000, "COMPLETED", null, p.getProviderOrderId()));
    assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("match");
  }

  @Test
  void missingRefundUsesRecordedFoodAmountAndStableReference() {
    var p = payment();
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenThrow(new PaymentGatewayException("PHONEPE_REFUND_NOT_FOUND", "Not found", false));
    when(client.refund(p.getRefundReferenceId(), p.getProviderOrderId(), p.getRefundAmount()))
        .thenReturn(new PhonePeClient.RefundResponse("refund1", 10000, "PENDING", null, null));
    assertThat(provider.refund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    verify(client).refund(p.getRefundReferenceId(), p.getProviderOrderId(), new BigDecimal("100"));
  }

  @Test
  void onlyExplicitRefundNotFoundAllowsInitiation() {
    var realClient = new PhonePeClient(new PhonePeProperties(),new tools.jackson.databind.ObjectMapper());
    java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
    when(response.statusCode()).thenReturn(404);
    when(response.body()).thenReturn("{\"code\":\"REFUND_NOT_FOUND\"}");
    assertThat((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(realClient,"refundNotFound",response)).isTrue();
    when(response.body()).thenReturn("{\"code\":\"ENDPOINT_NOT_FOUND\"}");
    assertThat((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(realClient,"refundNotFound",response)).isFalse();
    when(response.statusCode()).thenReturn(503);
    when(response.body()).thenReturn("{\"code\":\"REFUND_NOT_FOUND\"}");
    assertThat((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(realClient,"refundNotFound",response)).isFalse();
  }

  @Test
  void refundNotFoundIsScopedToStatusGetAndMalformedSuccessRemainsUncertain() {
    var real = new PhonePeClient(new PhonePeProperties(), new tools.jackson.databind.ObjectMapper());
    java.net.http.HttpResponse<String> response = mock(java.net.http.HttpResponse.class);
    when(response.statusCode()).thenReturn(404);
    when(response.body()).thenReturn("{\"code\":\"REFUND_NOT_FOUND\"}");
    assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(real,
        "handleApiResponse", response, "GET", "/payments/v2/refund/ref/status"))
        .isInstanceOfSatisfying(PaymentGatewayException.class, e -> assertThat(e.getCode()).isEqualTo("PHONEPE_REFUND_NOT_FOUND"));
    assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(real,
        "handleApiResponse", response, "POST", "/payments/v2/refund"))
        .isInstanceOfSatisfying(PaymentGatewayException.class, e -> assertThat(e.getCode()).isEqualTo("PHONEPE_REQUEST_REJECTED"));
    when(response.statusCode()).thenReturn(200);
    tools.jackson.databind.JsonNode node = org.springframework.test.util.ReflectionTestUtils.invokeMethod(real,
        "handleApiResponse", response, "GET", "/payments/v2/refund/ref/status");
    assertThatThrownBy(() -> real.refundResponse(node))
        .isInstanceOfSatisfying(PaymentGatewayException.class, e -> assertThat(e.getCode()).isEqualTo("PHONEPE_REFUND_INVALID_RESPONSE"));
    assertThatThrownBy(() -> real.refundResponse(null)).isInstanceOf(PaymentGatewayException.class);
  }

  @Test
  void uncertaintyNeverStartsAnotherRefund() {
    var p = payment();
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenThrow(new PaymentGatewayException("PHONEPE_REQUEST_REJECTED", "Unavailable", true));
    assertThatThrownBy(() -> provider.refund(p)).isInstanceOf(PaymentGatewayException.class);
    verify(client, never()).refund(anyString(), anyString(), any());
  }

  @Test
  void mismatchedStatusAmountOrOrderIsRejected() {
    var p = payment();
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenReturn(
            new PhonePeClient.RefundResponse(
                "refund1", 11700, "COMPLETED", null, p.getProviderOrderId()));
    assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("match");
    when(client.refundStatus(p.getRefundReferenceId()))
        .thenReturn(
            new PhonePeClient.RefundResponse(
                "refund1", 10000, "COMPLETED", null, "other-order"));
    assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("match");
  }
  @Test
  void refundParserRejectsFractionalOverflowNonpositiveAndMissingAmounts() throws Exception {
    var mapper = new tools.jackson.databind.ObjectMapper();
    var real = new PhonePeClient(new PhonePeProperties(), mapper);
    for (var amount : java.util.List.of("10000.5", "9223372036854775808", "0", "-1", "null", "\"10000\"")) {
      var body = mapper.readTree("{\"refundId\":\"refund1\",\"state\":\"COMPLETED\",\"amount\":" + amount + "}");
      assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(real,"refundResponse",body))
          .isInstanceOf(PaymentGatewayException.class);
    }
    var body = mapper.readTree("{\"refundId\":\"refund1\",\"state\":\"COMPLETED\",\"amount\":10000,\"originalMerchantOrderId\":\"DEV-payment-1\"}");
    PhonePeClient.RefundResponse parsed = org.springframework.test.util.ReflectionTestUtils.invokeMethod(real,"refundResponse",body);
    assertThat(parsed.amount()).isEqualTo(10000);
    assertThat(parsed.merchantRefundId()).isNull();
    assertThat(parsed.originalMerchantOrderId()).isEqualTo("DEV-payment-1");
  }
}
