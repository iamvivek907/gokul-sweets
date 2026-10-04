package com.gokulsweets.restaurant.payment.provider.razorpay;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class RazorpayRefundTest {
  final RazorpayClient client = mock(RazorpayClient.class);
  final RazorpayPaymentProvider provider = new RazorpayPaymentProvider(client, new RazorpayProperties());

  Payment payment() {
    var p = new Payment();
    p.setProviderPaymentId("pay_1");
    p.setAmount(new BigDecimal("117"));
    p.setRefundAmount(new BigDecimal("100"));
    p.setRefundReferenceId("GKC-stable-reference");
    return p;
  }

  @Test
  void validatesPartialInitiationAndRecoveryAndLegacyFullRefund() {
    var p = payment();
    when(client.createRefund("pay_1", new BigDecimal("100"), p.getRefundReferenceId()))
        .thenReturn(new RazorpayClient.ProviderRefund("rf_1", "pending", "pay_1", 10000));
    assertThat(provider.refund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    p.setProviderRefundId("rf_1");
    when(client.fetchRefund("rf_1"))
        .thenReturn(new RazorpayClient.ProviderRefund("rf_1", "processed", "pay_1", 10000));
    assertThat(provider.verifyRefund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    p.setRefundAmount(null);
    when(client.fetchRefund("rf_1"))
        .thenReturn(new RazorpayClient.ProviderRefund("rf_1", "processed", "pay_1", 11700));
    assertThat(provider.verifyRefund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void rejectsWrongPaymentAndAmountForBothInitiationAndRecovery() {
    var p = payment();
    for (var refund : java.util.List.of(
        new RazorpayClient.ProviderRefund("rf_1", "processed", "other_payment", 10000),
        new RazorpayClient.ProviderRefund("rf_1", "pending", "pay_1", 11700),
        new RazorpayClient.ProviderRefund("rf_1", "processed", null, 10000),
        new RazorpayClient.ProviderRefund("rf_1", "processed", "pay_1", 0))) {
      when(client.createRefund(anyString(), any(), anyString())).thenReturn(refund);
      assertThatThrownBy(() -> provider.refund(p)).isInstanceOf(IllegalStateException.class);
      p.setProviderRefundId("rf_1");
      when(client.fetchRefund("rf_1")).thenReturn(refund);
      assertThatThrownBy(() -> provider.verifyRefund(p)).isInstanceOf(IllegalStateException.class);
    }
    when(client.fetchRefund("rf_1"))
        .thenReturn(new RazorpayClient.ProviderRefund("other_refund", "processed", "pay_1", 10000));
    assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("refund ID");
  }

  @Test
  void parsesAmountWithoutDefaultingMissingOrMalformedValuesToZero() throws Exception {
    var mapper = new ObjectMapper();
    var real = new RazorpayClient(new RazorpayProperties(), mapper);
    var valid = mapper.readTree("{\"id\":\"rf_1\",\"status\":\"processed\",\"payment_id\":\"pay_1\",\"amount\":10000}");
    RazorpayClient.ProviderRefund parsed = ReflectionTestUtils.invokeMethod(real, "mapRefund", valid);
    assertThat(parsed.amount()).isEqualTo(10000);
    assertThat(parsed.paymentId()).isEqualTo("pay_1");
    for (var amount : java.util.List.of("null", "\"10000\"", "10000.5", "0", "-1", "9223372036854775808")) {
      var invalid = mapper.readTree("{\"id\":\"rf_1\",\"status\":\"processed\",\"payment_id\":\"pay_1\",\"amount\":" + amount + "}");
      assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(real, "mapRefund", invalid))
          .isInstanceOf(IllegalStateException.class);
    }
    var missing = mapper.readTree("{\"id\":\"rf_1\",\"status\":\"processed\",\"payment_id\":\"pay_1\"}");
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(real, "mapRefund", missing))
        .isInstanceOf(IllegalStateException.class);
  }
}
