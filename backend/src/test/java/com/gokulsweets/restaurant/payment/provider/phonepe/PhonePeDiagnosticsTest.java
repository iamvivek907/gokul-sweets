package com.gokulsweets.restaurant.payment.provider.phonepe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class PhonePeDiagnosticsTest {
    @Test
    void concurrentPaymentsShareOneTokenRefresh() throws Exception {
        var properties = properties();
        var client = new PhonePeClient(properties, new ObjectMapper());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        var tokenCalls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        call -> {
                            HttpRequest request = call.getArgument(0);
                            if (request.uri().getPath().equals("/v1/oauth/token")) {
                                tokenCalls.incrementAndGet();
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                return response(
                                        "{\"access_token\":\"test-token\",\"expires_at\":"
                                                + (Instant.now().getEpochSecond() + 3600)
                                                + "}");
                            }
                            assertThat(request.headers().firstValue("Authorization"))
                                    .contains("O-Bearer test-token");
                            return response("{\"state\":\"PENDING\"}");
                        });
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Future<PhonePeClient.StatusResponse>> calls = new ArrayList<>();
            calls.add(executor.submit(() -> client.checkStatus("order0")));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 1; index < 12; index++) {
                String id = "order" + index;
                calls.add(executor.submit(() -> client.checkStatus(id)));
            }
            release.countDown();
            for (var call : calls)
                assertThat(call.get(5, TimeUnit.SECONDS).state()).isEqualTo("PENDING");
            assertThat(tokenCalls.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void tokenReuseHonoursConfiguredSafetyWindow() throws Exception {
        var properties = properties();
        properties.setTokenExpirySafetySeconds(120);
        var client = new PhonePeClient(properties, new ObjectMapper());
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(client, "httpClient", http);
        var tokenCalls = new AtomicInteger();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        call -> {
                            HttpRequest request = call.getArgument(0);
                            if (request.uri().getPath().equals("/v1/oauth/token")) {
                                int seconds = tokenCalls.incrementAndGet() == 1 ? 90 : 3600;
                                return response(
                                        "{\"access_token\":\"test-token\",\"expires_at\":"
                                                + (Instant.now().getEpochSecond() + seconds)
                                                + "}");
                            }
                            return response("{\"state\":\"PENDING\"}");
                        });
        client.checkStatus("order1");
        client.checkStatus("order2");
        client.checkStatus("order3");
        assertThat(tokenCalls.get()).isEqualTo(2);
    }

    @Test
    void hmacUsesExactUtf8BodyAndRejectsReformattedOrIncorrectlySignedPayloads() throws Exception {
        var properties = properties();
        properties.setWebhookChecksumKeyId("test-key");
        properties.setWebhookChecksumSecret("test-secret");
        var client = new PhonePeClient(properties, new ObjectMapper());
        byte[] body =
                "{\"event\": \"checkout.order.completed\", \"name\": \"मिठाई\"}\n"
                        .getBytes(StandardCharsets.UTF_8);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body));
        assertThatCode(() -> client.verifyWebhookSignature(body, "test-key", signature))
                .doesNotThrowAnyException();
        byte[] changed =
                new String(body, StandardCharsets.UTF_8)
                        .replace(": ", ":")
                        .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> client.verifyWebhookSignature(changed, "test-key", signature))
                .isInstanceOf(PaymentSignatureException.class);
        assertThatThrownBy(() -> client.verifyWebhookSignature(body, "other-key", signature))
                .isInstanceOf(PaymentSignatureException.class);
        assertThatThrownBy(() -> client.verifyWebhookSignature(body, "test-key", "0".repeat(64)))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void allSupportedEncodingsAuthenticateTheSameDigestAndRejectTampering() throws Exception {
        var properties = properties();
        properties.setWebhookChecksumKeyId("test-key");
        properties.setWebhookChecksumSecret("test-secret");
        var client = new PhonePeClient(properties, new ObjectMapper());
        byte[] body =
                "{\"event\":\"checkout.order.completed\",\"name\":\"मिठाई\"}\n"
                        .getBytes(StandardCharsets.UTF_8);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body);
        var signatures =
                List.of(
                        HexFormat.of().formatHex(digest),
                        HexFormat.of().withUpperCase().formatHex(digest),
                        Base64.getEncoder().encodeToString(digest));
        for (String signature : signatures) {
            assertThatCode(() -> client.verifyWebhookSignature(body, "test-key", signature))
                    .doesNotThrowAnyException();
            byte[] changed = Arrays.copyOf(body, body.length - 1);
            assertThatThrownBy(() -> client.verifyWebhookSignature(changed, "test-key", signature))
                    .isInstanceOf(PaymentSignatureException.class);
            assertThatThrownBy(() -> client.verifyWebhookSignature(body, "wrong-key", signature))
                    .isInstanceOf(PaymentSignatureException.class);
            properties.setWebhookChecksumSecret("wrong-secret");
            assertThatThrownBy(() -> client.verifyWebhookSignature(body, "test-key", signature))
                    .isInstanceOf(PaymentSignatureException.class);
            properties.setWebhookChecksumSecret("test-secret");
        }
    }

    @Test
    void malformedTruncatedAndIncorrectDigestsRemainRejected() throws Exception {
        var properties = properties();
        properties.setWebhookChecksumKeyId("test-key");
        properties.setWebhookChecksumSecret("test-secret");
        var client = new PhonePeClient(properties, new ObjectMapper());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String valid = Base64.getEncoder().encodeToString(mac.doFinal(body));
        for (String invalid :
                List.of(
                        "!".repeat(44),
                        "0".repeat(63),
                        "g".repeat(64),
                        valid.substring(0, 43),
                        valid.substring(0, 20) + " " + valid.substring(21),
                        Base64.getEncoder().encodeToString(new byte[31]),
                        Base64.getEncoder().encodeToString(new byte[33]),
                        Base64.getEncoder().encodeToString(new byte[32]),
                        "0".repeat(64))) {
            assertThatThrownBy(() -> client.verifyWebhookSignature(body, "test-key", invalid))
                    .isInstanceOf(PaymentSignatureException.class);
        }
        // Noncanonical padding bits can decode to the valid digest; require canonical text.
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int lastIndex = alphabet.indexOf(valid.charAt(42));
        String noncanonical = valid.substring(0, 42) + alphabet.charAt(lastIndex + 1) + "=";
        assertThat(Base64.getDecoder().decode(noncanonical))
                .isEqualTo(Base64.getDecoder().decode(valid));
        assertThatThrownBy(() -> client.verifyWebhookSignature(body, "test-key", noncanonical))
                .isInstanceOf(PaymentSignatureException.class);
    }

    private PhonePeProperties properties() {
        var properties = new PhonePeProperties();
        properties.setClientId("test-client");
        properties.setClientSecret("test-secret");
        properties.setClientVersion("1");
        properties.setBaseUrl("https://phonepe-test.invalid");
        properties.setAuthorizationBaseUrl("https://phonepe-auth-test.invalid");
        return properties;
    }

    private HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }
}
