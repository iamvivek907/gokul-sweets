package com.gokulsweets.restaurant.payment.provider.razorpay;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exercises the actual HTTP adapter against a disposable merchant fixture, not live credentials.
 */
class RazorpayAdapterJourneyTest {
    HttpServer server;
    RazorpayPaymentProvider provider;
    Payment payment;
    volatile String paymentState = "authorized", refundState = "pending", providerOrder = "order_1";
    volatile int responseStatus = 200;
    List<String> refundKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                            .startsWith("Basic ");
                    String path = exchange.getRequestURI().getPath(), json;
                    if (path.equals("/v1/orders")) {
                        var body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                        assertThat(body.path("amount").asLong()).isEqualTo(20000);
                        assertThat(body.path("currency").asText()).isEqualTo("INR");
                        json = "{\"id\":\"order_1\",\"status\":\"created\",\"amount\":20000}";
                    } else if (path.equals("/v1/payments/pay_1/refund")) {
                        refundKeys.add(
                                exchange.getRequestHeaders().getFirst("X-Refund-Idempotency"));
                        var body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                        assertThat(body.path("amount").asLong()).isEqualTo(10000);
                        assertThat(body.path("receipt").asText()).isEqualTo("refund_1");
                        json = refundJson();
                    } else if (path.equals("/v1/refunds/rfnd_1")) json = refundJson();
                    else {
                        String entity =
                                "{\"id\":\"pay_1\",\"order_id\":\""
                                        + providerOrder
                                        + "\",\"status\":\""
                                        + paymentState
                                        + "\",\"amount\":20000}";
                        json = path.endsWith("/payments") ? "{\"items\":[" + entity + "]}" : entity;
                    }
                    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        var properties = new RazorpayProperties();
        properties.setKeyId("rzp_test_fixture");
        properties.setKeySecret("fixture-only");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        provider = new RazorpayPaymentProvider(new RazorpayClient(properties, mapper), properties);
        payment = new Payment();
        payment.setId(1L);
        payment.setAmount(new BigDecimal("200"));
        payment.setCurrency("INR");
        var order = new Order();
        order.setOrderNumber("ORDER_1");
        payment.setOrder(order);
        payment.setProviderOrderId("order_1");
        payment.setRefundAmount(new BigDecimal("100"));
        payment.setRefundReferenceId("refund_1");
    }

    String refundJson() {
        return "{\"id\":\"rfnd_1\",\"payment_id\":\"pay_1\",\"status\":\""
                + refundState
                + "\",\"amount\":10000}";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void orderCaptureAndPartialRefundUseRealAdapterRequestsAndProviderIds() {
        assertThat(provider.createPayment(payment.getOrder(), payment).providerOrderId())
                .isEqualTo("order_1");
        assertThat(provider.verifyPayment(payment).paymentStatus())
                .isEqualTo(PaymentStatus.PENDING);
        paymentState = "captured";
        var captured = provider.verifyPayment(payment);
        assertThat(captured.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        payment.setProviderPaymentId(captured.providerPaymentId());
        var refund = provider.refund(payment);
        assertThat(refund.paymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        payment.setProviderRefundId(refund.providerRefundId());
        refundState = "processed";
        assertThat(provider.verifyRefund(payment).paymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getProviderRefundId()).isEqualTo("rfnd_1");
    }

    @Test
    void refundRetryUsesSameIdempotencyIdentityAndFailureIsExplicit() {
        payment.setProviderPaymentId("pay_1");
        var first = provider.refund(payment);
        var retry = provider.refund(payment);
        assertThat(first.providerRefundId()).isEqualTo(retry.providerRefundId());
        assertThat(refundKeys).containsExactly("gokul-refund-refund_1", "gokul-refund-refund_1");
        payment.setProviderRefundId(first.providerRefundId());
        refundState = "failed";
        assertThat(provider.verifyRefund(payment).paymentStatus())
                .isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void failedPaymentAndWrongOrderCannotBeConfirmed() {
        paymentState = "failed";
        assertThat(provider.verifyPayment(payment).paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        paymentState = "captured";
        providerOrder = "someone-elses-order";
        assertThatThrownBy(() -> provider.verifyPayment(payment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void merchantOutageRaisesRetryableFailureInsteadOfReportingPaymentSuccess() {
        responseStatus = 503;
        assertThatThrownBy(() -> provider.verifyPayment(payment))
                .isInstanceOf(
                        com.gokulsweets.restaurant.payment.exception.PaymentGatewayException.class);
    }
}
