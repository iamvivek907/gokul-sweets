package com.gokulsweets.restaurant.payment.provider.paytm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.service.PaymentReconciliationPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class PaytmRefundTest {
  final PaytmClient client = mock(PaytmClient.class);
  final PaytmPaymentProvider provider = new PaytmPaymentProvider(client, mock(PaymentReconciliationPolicy.class));

  Payment payment() {
    var p = new Payment(); p.setAmount(new BigDecimal("117"));
    p.setRefundAmount(new BigDecimal("100")); p.setProviderOrderId("order1");
    p.setProviderPaymentId("txn1"); p.setRefundReferenceId("GKC-stable"); return p;
  }

  PaytmRefundGatewayResult response(String status, String code, BigDecimal amount, String reference, String order, String txn) {
    return new PaytmRefundGatewayResult(status,code,null,"refund1",amount,reference,order,txn);
  }

  @Test
  void validatesPartialInitiationStatusRecoveryAndLegacyFullRefund() {
    var p = payment();
    when(client.initiateRefund("order1","txn1","GKC-stable",new BigDecimal("100")))
        .thenReturn(response("PENDING","601",new BigDecimal("100.00"),"GKC-stable","order1","txn1"));
    assertThat(provider.refund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    verify(client).initiateRefund("order1","txn1","GKC-stable",new BigDecimal("100"));
    p.setProviderRefundId("refund1");
    when(client.getRefundStatus("order1","GKC-stable"))
        .thenReturn(response("TXN_SUCCESS","10",new BigDecimal("100"),"GKC-stable","order1","txn1"));
    assertThat(provider.verifyRefund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    p.setRefundAmount(null);
    when(client.getRefundStatus("order1","GKC-stable"))
        .thenReturn(response("TXN_FAILURE","629",new BigDecimal("117"),"GKC-stable","order1","txn1"));
    assertThat(provider.verifyRefund(p).paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void bothResponsesRejectWrongOrMissingAmountsAndReferencesIncludingAlreadySuccessfulCode() {
    var p = payment();
    for (var bad : java.util.List.of(
        response("TXN_SUCCESS","10",new BigDecimal("117"),"GKC-stable","order1","txn1"),
        response("TXN_FAILURE","629",new BigDecimal("100"),"wrong","order1","txn1"),
        response("PENDING","601",new BigDecimal("100"),"GKC-stable","wrong","txn1"),
        response("TXN_SUCCESS","10",new BigDecimal("100"),"GKC-stable","order1","wrong"),
        response("TXN_SUCCESS","10",null,"GKC-stable","order1","txn1"),
        response("TXN_SUCCESS","10",new BigDecimal("100"),null,"order1","txn1"))) {
      when(client.initiateRefund(anyString(),anyString(),anyString(),any())).thenReturn(bad);
      when(client.getRefundStatus(anyString(),anyString())).thenReturn(bad);
      assertThatThrownBy(() -> provider.refund(p)).hasMessageContaining("match");
      assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("match");
    }
    p.setProviderRefundId("other-refund");
    when(client.getRefundStatus("order1","GKC-stable"))
        .thenReturn(response("TXN_SUCCESS","10",new BigDecimal("100"),"GKC-stable","order1","txn1"));
    assertThatThrownBy(() -> provider.verifyRefund(p)).hasMessageContaining("match");
  }

  @Test
  void parsesResponseIdentityAndExactDecimalRupeesWithoutRounding() {
    var real = new PaytmClient(new PaytmProperties(),new ObjectMapper());
    String fields="\"resultInfo\":{\"resultStatus\":\"PENDING\",\"resultCode\":\"601\"},\"refundId\":\"refund1\",\"refId\":\"GKC-stable\",\"orderId\":\"order1\",\"txnId\":\"txn1\"";
    PaytmRefundGatewayResult parsed = ReflectionTestUtils.invokeMethod(real,"parseRefundGatewayResult",
        "{\"body\":{"+fields+",\"refundAmount\":\"100.00\"}}","order1","GKC-stable");
    assertThat(parsed.refundAmount()).isEqualByComparingTo("100");
    assertThat(parsed.refId()).isEqualTo("GKC-stable"); assertThat(parsed.orderId()).isEqualTo("order1");
    assertThat(parsed.txnId()).isEqualTo("txn1");
    for (var value : java.util.List.of("\"100.005\"", "100.005", "0", "-1", "\"NaN\"", "{}")) {
      assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(real,"parseRefundGatewayResult",
          "{\"body\":{"+fields+",\"refundAmount\":"+value+"}}","order1","GKC-stable"))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
