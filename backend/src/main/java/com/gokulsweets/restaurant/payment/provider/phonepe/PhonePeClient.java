package com.gokulsweets.restaurant.payment.provider.phonepe;

import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Component
@Slf4j
public class PhonePeClient {

    private final PhonePeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PhonePeClient(
            PhonePeProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        properties.getConnectTimeoutSeconds()
                ))
                .build();
    }

    public CreatePaymentResponse createPayment(
            String merchantTransactionId,
            BigDecimal amount,
            String redirectUrl,
            String callbackUrl
    ) {
        properties.requireApiConfiguration();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("merchantId", properties.getMerchantId());
        payload.put("merchantTransactionId", merchantTransactionId);
        payload.put("merchantUserId", merchantTransactionId);
        payload.put("amount", toSubunits(amount));
        payload.put("redirectMode", "POST");
        payload.put("redirectUrl", redirectUrl);
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            payload.put("callbackUrl", callbackUrl);
        }
        payload.putObject("paymentInstrument")
                .put("type", "PAY_PAGE");

        String encodedPayload = encodePayload(payload);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("request", encodedPayload);

        String path = normalizedPath(properties.getCreatePaymentPath());
        JsonNode response = send(
                "POST",
                path,
                body,
                signatureForPayload(encodedPayload, path)
        );

        return new CreatePaymentResponse(
                response.path("success").asBoolean(false),
                textOrNull(response.path("data"), "merchantTransactionId"),
                textOrNull(response.path("data"), "transactionId"),
                response.path("data")
                        .path("instrumentResponse")
                        .path("redirectInfo")
                        .path("url")
                        .asText(null),
                textOrNull(response, "message")
        );
    }

    public StatusResponse checkStatus(
            String merchantTransactionId
    ) {
        properties.requireApiConfiguration();

        String path = properties.getStatusPathTemplate()
                .replace("{merchantId}", properties.getMerchantId())
                .replace("{merchantTransactionId}", merchantTransactionId);
        path = normalizedPath(path);

        JsonNode response = send(
                "GET",
                path,
                null,
                signatureForStatus(path)
        );

        JsonNode data = response.path("data");
        return new StatusResponse(
                response.path("success").asBoolean(false),
                data.path("state").asText("PENDING"),
                textOrNull(data, "transactionId"),
                textOrNull(data, "responseCode"),
                textOrNull(response, "message")
        );
    }

    public void verifyCallbackSignature(
            byte[] rawBody,
            String signature,
            String callbackPath
    ) {
        properties.requireApiConfiguration();
        if (signature == null || signature.isBlank()) {
            throw new PaymentSignatureException(
                    "PHONEPE_SIGNATURE_REQUIRED",
                    "PhonePe callback signature is required."
            );
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "PhonePe callback payload is invalid.",
                    exception
            );
        }

        String encodedResponse = textOrNull(root, "response");
        if (encodedResponse == null || encodedResponse.isBlank()) {
            throw new IllegalArgumentException(
                    "PhonePe callback payload is missing response data."
            );
        }

        String expected = sha256(
                encodedResponse
                        + normalizedPath(callbackPath)
                        + properties.getSaltKey()
        ) + "###" + properties.getSaltIndex();

        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new PaymentSignatureException(
                    "INVALID_PHONEPE_SIGNATURE",
                    "PhonePe callback signature is invalid."
            );
        }
    }

    public JsonNode decodeCallbackResponse(
            String encodedResponse
    ) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedResponse);
            return objectMapper.readTree(decoded);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "PhonePe callback response is invalid.",
                    exception
            );
        }
    }

    private JsonNode send(
            String method,
            String path,
            JsonNode body,
            String signature
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + path))
                    .timeout(Duration.ofSeconds(
                            properties.getRequestTimeoutSeconds()
                    ))
                    .header("Accept", "application/json")
                    .header("X-VERIFY", signature)
                    .header("X-MERCHANT-ID", properties.getMerchantId());

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(
                                method,
                                HttpRequest.BodyPublishers.ofString(
                                        objectMapper.writeValueAsString(body)
                                )
                        );
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 429
                        || response.statusCode() >= 500;
                throw new PaymentGatewayException(
                        "PHONEPE_REQUEST_REJECTED",
                        retryable
                                ? "PhonePe is temporarily unavailable. Please try again."
                                : "PhonePe could not process this payment request.",
                        retryable
                );
            }

            return objectMapper.readTree(response.body());

        } catch (PaymentGatewayException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(
                    "PHONEPE_REQUEST_INTERRUPTED",
                    "PhonePe payment request was interrupted. Please try again.",
                    true,
                    exception
            );
        } catch (IOException | RuntimeException exception) {
            throw new PaymentGatewayException(
                    "PHONEPE_CONNECTION_FAILED",
                    "Unable to reach PhonePe right now. Please try again.",
                    true,
                    exception
            );
        }
    }

    private String signatureForPayload(String encodedPayload, String path) {
        return sha256(encodedPayload + path + properties.getSaltKey())
                + "###"
                + properties.getSaltIndex();
    }

    private String signatureForStatus(String path) {
        return sha256(path + properties.getSaltKey())
                + "###"
                + properties.getSaltIndex();
    }

    private String encodePayload(ObjectNode payload) {
        try {
            return Base64.getEncoder().encodeToString(
                    objectMapper.writeValueAsBytes(payload)
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Unable to encode PhonePe request payload.",
                    exception
            );
        }
    }

    private String normalizedBaseUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "");
    }

    private String normalizedPath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    private long toSubunits(BigDecimal amount) {
        try {
            return amount
                    .setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Payment amount has an unsupported precision.",
                    exception
            );
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    public record CreatePaymentResponse(
            boolean success,
            String merchantTransactionId,
            String transactionId,
            String redirectUrl,
            String message
    ) {
    }

    public record StatusResponse(
            boolean success,
            String state,
            String transactionId,
            String responseCode,
            String message
    ) {
    }
}
