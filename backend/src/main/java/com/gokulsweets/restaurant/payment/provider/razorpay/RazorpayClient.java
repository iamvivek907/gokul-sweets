package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Component
@Slf4j
public class RazorpayClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RazorpayClient(
            RazorpayProperties properties,
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

    public CreatedOrder createOrder(
            BigDecimal amount,
            String currency,
            String receipt,
            String businessOrderNumber
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", toSubunits(amount));
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.putObject("notes")
                .put("business_order_number", businessOrderNumber);

        JsonNode response = send("POST", "/v1/orders", body);
        return new CreatedOrder(
                requiredText(response, "id"),
                requiredText(response, "status"),
                response.path("amount").asLong()
        );
    }

    public ProviderPayment fetchPayment(String providerPaymentId) {
        JsonNode response = send(
                "GET",
                "/v1/payments/" + encodePath(providerPaymentId),
                null
        );
        return mapPayment(response);
    }

    public ProviderPayment findBestPaymentForOrder(
            String providerOrderId
    ) {
        JsonNode response = send(
                "GET",
                "/v1/orders/" + encodePath(providerOrderId) + "/payments",
                null
        );

        ProviderPayment pending = null;
        ProviderPayment failed = null;

        for (JsonNode item : response.path("items")) {
            ProviderPayment payment = mapPayment(item);
            if ("captured".equals(payment.status())) {
                return payment;
            }
            if ("authorized".equals(payment.status())
                    || "created".equals(payment.status())) {
                pending = payment;
            } else if ("failed".equals(payment.status())) {
                failed = payment;
            }
        }

        return pending != null ? pending : failed;
    }

    public ProviderRefund createRefund(
            String providerPaymentId,
            BigDecimal amount,
            String refundReference
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", toSubunits(amount));
        body.put("receipt", refundReference);
        body.putObject("notes")
                .put("refund_reference", refundReference);

        JsonNode response = send(
                "POST",
                "/v1/payments/" + encodePath(providerPaymentId) + "/refund",
                body
        );
        return mapRefund(response);
    }

    public ProviderRefund fetchRefund(String providerRefundId) {
        JsonNode response = send(
                "GET",
                "/v1/refunds/" + encodePath(providerRefundId),
                null
        );
        return mapRefund(response);
    }

    public void verifyCheckoutSignature(
            String providerOrderId,
            String providerPaymentId,
            String signature
    ) {
        properties.requireApiConfiguration();
        requireSignature(signature);

        String payload = providerOrderId + "|" + providerPaymentId;
        verifyHmac(
                payload.getBytes(StandardCharsets.UTF_8),
                signature,
                properties.getKeySecret(),
                "INVALID_RAZORPAY_SIGNATURE"
        );
    }

    public void verifyWebhookSignature(
            byte[] rawBody,
            String signature
    ) {
        properties.requireWebhookConfiguration();
        requireSignature(signature);
        verifyHmac(
                rawBody,
                signature,
                properties.getWebhookSecret(),
                "INVALID_RAZORPAY_WEBHOOK_SIGNATURE"
        );
    }

    private JsonNode send(
            String method,
            String path,
            JsonNode body
    ) {
        properties.requireApiConfiguration();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + path))
                    .timeout(Duration.ofSeconds(
                            properties.getRequestTimeoutSeconds()
                    ))
                    .header("Authorization", basicAuthorization())
                    .header("Accept", "application/json");

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
                String providerMessage = safeProviderMessage(response.body());
                boolean retryable = response.statusCode() == 429
                        || response.statusCode() >= 500;

                log.warn(
                        "Razorpay API rejected request: path={}, status={}, retryable={}, providerMessage={}",
                        path,
                        response.statusCode(),
                        retryable,
                        providerMessage
                );

                throw new PaymentGatewayException(
                        "RAZORPAY_REQUEST_REJECTED",
                        retryable
                                ? "The payment service is temporarily unavailable. Please try again."
                                : "Razorpay could not process this payment request.",
                        retryable
                );
            }

            return objectMapper.readTree(response.body());

        } catch (PaymentGatewayException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(
                    "RAZORPAY_REQUEST_INTERRUPTED",
                    "The payment request was interrupted. Please try again.",
                    true,
                    exception
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new PaymentGatewayException(
                    "RAZORPAY_CONNECTION_FAILED",
                    "Unable to reach the payment service. Please try again.",
                    true,
                    exception
            );
        }
    }

    private ProviderPayment mapPayment(JsonNode node) {
        return new ProviderPayment(
                requiredText(node, "id"),
                textOrNull(node, "order_id"),
                requiredText(node, "status"),
                node.path("amount").asLong(),
                textOrNull(node, "error_description")
        );
    }

    private ProviderRefund mapRefund(JsonNode node) {
        return new ProviderRefund(
                requiredText(node, "id"),
                requiredText(node, "status"),
                textOrNull(node, "payment_id")
        );
    }

    private void verifyHmac(
            byte[] payload,
            String suppliedSignature,
            String secret,
            String errorCode
    ) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));

            byte[] expected = mac.doFinal(payload);
            byte[] supplied;
            try {
                supplied = HexFormat.of().parseHex(suppliedSignature.trim());
            } catch (IllegalArgumentException exception) {
                throw new PaymentSignatureException(
                        errorCode,
                        "Payment verification signature is invalid."
                );
            }

            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new PaymentSignatureException(
                        errorCode,
                        "Payment verification signature is invalid."
                );
            }
        } catch (PaymentSignatureException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is unavailable.",
                    exception
            );
        }
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

    private String basicAuthorization() {
        String credentials = properties.getKeyId()
                + ":"
                + properties.getKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizedBaseUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "");
    }

    private String encodePath(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Provider identifier is invalid."
            );
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) {
            throw new PaymentGatewayException(
                    "INVALID_RAZORPAY_RESPONSE",
                    "Razorpay returned an incomplete response.",
                    true
            );
        }
        return value;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private String safeProviderMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String description = root.path("error")
                    .path("description")
                    .asText("");
            return description.length() > 200
                    ? description.substring(0, 200)
                    : description;
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private void requireSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            throw new PaymentSignatureException(
                    "PAYMENT_SIGNATURE_REQUIRED",
                    "Payment verification signature is required."
            );
        }
    }

    public record CreatedOrder(
            String id,
            String status,
            long amount
    ) {
    }

    public record ProviderPayment(
            String id,
            String orderId,
            String status,
            long amount,
            String failureReason
    ) {
    }

    public record ProviderRefund(
            String id,
            String status,
            String paymentId
    ) {
    }
}
