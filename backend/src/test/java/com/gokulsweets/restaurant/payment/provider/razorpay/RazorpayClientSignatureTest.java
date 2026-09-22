package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RazorpayClientSignatureTest {

    private static final String API_SECRET = "test-api-secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private RazorpayClient client;

    @BeforeEach
    void setUp() {
        RazorpayProperties properties = new RazorpayProperties();
        properties.setKeyId("rzp_test_example");
        properties.setKeySecret(API_SECRET);
        properties.setWebhookSecret(WEBHOOK_SECRET);
        client = new RazorpayClient(properties, new ObjectMapper());
    }

    @Test
    void acceptsValidCheckoutSignature() {
        String orderId = "order_example";
        String paymentId = "pay_example";
        String signature = hmac(
                (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8),
                API_SECRET
        );

        assertDoesNotThrow(() -> client.verifyCheckoutSignature(
                orderId,
                paymentId,
                signature
        ));
    }

    @Test
    void rejectsInvalidCheckoutSignature() {
        assertThrows(
                PaymentSignatureException.class,
                () -> client.verifyCheckoutSignature(
                        "order_example",
                        "pay_example",
                        "00".repeat(32)
                )
        );
    }

    @Test
    void acceptsValidRawWebhookSignature() {
        byte[] payload = "{\"event\":\"payment.captured\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> client.verifyWebhookSignature(
                payload,
                hmac(payload, WEBHOOK_SECRET)
        ));
    }

    private String hmac(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
