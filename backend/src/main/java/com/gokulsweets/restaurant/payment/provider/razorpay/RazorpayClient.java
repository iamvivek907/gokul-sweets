package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.gokulsweets.restaurant.observability.MethodTiming;
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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Backend razorpay client contract and implementation. */
@Component
@Slf4j
public class RazorpayClient {

    private static final String HMAC_ALGORITHM = AppConstant.RAZORPAY_CLIENT_HMAC_ALGORITHM;

    private final RazorpayProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    /**
     * Creates a razorpay client instance.
     *
     * @param properties the properties
     * @param objectMapper the object mapper
     */
    public RazorpayClient(RazorpayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                        .build();
    }

    /**
     * Creates order.
     *
     * @param amount the amount
     * @param currency the currency
     * @param receipt the receipt
     * @param businessOrderNumber the business order number
     * @return the create order result
     */
    public CreatedOrder createOrder(
            BigDecimal amount, String currency, String receipt, String businessOrderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayClient.class, "createOrder(BigDecimal,String,String,String)");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("amount", toSubunits(amount));
            body.put("currency", currency);
            body.put("receipt", receipt);
            body.putObject("notes").put("business_order_number", businessOrderNumber);
            JsonNode response = send("POST", "/v1/orders", body);
            return new CreatedOrder(
                    requiredText(response, "id"),
                    requiredText(response, "status"),
                    response.path("amount").asLong());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "createOrder(BigDecimal,String,String,String)");
        }
    }

    /**
     * Fetches payment.
     *
     * @param providerPaymentId the provider payment id
     * @return the fetch payment result
     */
    public ProviderPayment fetchPayment(String providerPaymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "fetchPayment(String)");
        try {
            JsonNode response = send("GET", "/v1/payments/" + encodePath(providerPaymentId), null);
            return mapPayment(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "fetchPayment(String)");
        }
    }

    /**
     * Finds best payment for order.
     *
     * @param providerOrderId the provider order id
     * @return the find best payment for order result
     */
    public ProviderPayment findBestPaymentForOrder(String providerOrderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "findBestPaymentForOrder(String)");
        try {
            JsonNode response =
                    send("GET", "/v1/orders/" + encodePath(providerOrderId) + "/payments", null);
            ProviderPayment pending = null;
            ProviderPayment failed = null;
            for (JsonNode item : response.path("items")) {
                ProviderPayment payment = mapPayment(item);
                if ("captured".equals(payment.status())) {
                    return payment;
                }
                if ("authorized".equals(payment.status()) || "created".equals(payment.status())) {
                    pending = payment;
                } else if ("failed".equals(payment.status())) {
                    failed = payment;
                }
            }
            return pending != null ? pending : failed;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "findBestPaymentForOrder(String)");
        }
    }

    /**
     * Creates refund.
     *
     * @param providerPaymentId the provider payment id
     * @param amount the amount
     * @param refundReference the refund reference
     * @return the create refund result
     */
    public ProviderRefund createRefund(
            String providerPaymentId, BigDecimal amount, String refundReference) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "createRefund(String,BigDecimal,String)");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("amount", toSubunits(amount));
            body.put("receipt", refundReference);
            body.putObject("notes").put("refund_reference", refundReference);
            JsonNode response =
                    send(
                            "POST",
                            "/v1/payments/" + encodePath(providerPaymentId) + "/refund",
                            body,
                            "gokul-refund-" + refundReference);
            return mapRefund(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "createRefund(String,BigDecimal,String)");
        }
    }

    /**
     * Fetches refund.
     *
     * @param providerRefundId the provider refund id
     * @return the fetch refund result
     */
    public ProviderRefund fetchRefund(String providerRefundId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "fetchRefund(String)");
        try {
            JsonNode response = send("GET", "/v1/refunds/" + encodePath(providerRefundId), null);
            return mapRefund(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "fetchRefund(String)");
        }
    }

    /**
     * Verify checkout signature.
     *
     * @param providerOrderId the provider order id
     * @param providerPaymentId the provider payment id
     * @param signature the signature
     */
    public void verifyCheckoutSignature(
            String providerOrderId, String providerPaymentId, String signature) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayClient.class, "verifyCheckoutSignature(String,String,String)");
        try {
            properties.requireApiConfiguration();
            requireSignature(signature);
            String payload = providerOrderId + "|" + providerPaymentId;
            verifyHmac(
                    payload.getBytes(StandardCharsets.UTF_8),
                    signature,
                    properties.getKeySecret(),
                    "INVALID_RAZORPAY_SIGNATURE");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "verifyCheckoutSignature(String,String,String)");
        }
    }

    /**
     * Verify webhook signature.
     *
     * @param rawBody the raw body
     * @param signature the signature
     */
    public void verifyWebhookSignature(byte[] rawBody, String signature) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "verifyWebhookSignature(byte[],String)");
        try {
            properties.requireWebhookConfiguration();
            requireSignature(signature);
            verifyHmac(
                    rawBody,
                    signature,
                    properties.getWebhookSecret(),
                    "INVALID_RAZORPAY_WEBHOOK_SIGNATURE");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "verifyWebhookSignature(byte[],String)");
        }
    }

    /**
     * Sends razorpay data and returns the {@code JsonNode} result.
     *
     * @param method the method supplied to this method
     * @param path the path supplied to this method
     * @param body the body supplied to this method
     * @return the value of {@code send(method, path, body, null)}
     */
    private JsonNode send(String method, String path, JsonNode body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "send(String,String,JsonNode)");
        try {
            return send(method, path, body, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "send(String,String,JsonNode)");
        }
    }

    /**
     * Sends razorpay data and returns the {@code JsonNode} result.
     *
     * @param method the method supplied to this method
     * @param path the path supplied to this method
     * @param body the body supplied to this method
     * @param refundIdempotency the refund idempotency supplied to this method
     * @return the value of {@code objectMapper.readTree(response.body())}
     * @throws PaymentGatewayException when the method rejects the request with {@code
     *     RAZORPAY_CONNECTION_FAILED}; {@code RAZORPAY_REQUEST_INTERRUPTED}; {@code
     *     RAZORPAY_REQUEST_REJECTED}
     */
    private JsonNode send(String method, String path, JsonNode body, String refundIdempotency) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "send(String,String,JsonNode,String)");
        try {
            properties.requireApiConfiguration();
            try {
                HttpRequest.Builder builder =
                        HttpRequest.newBuilder()
                                .uri(URI.create(normalizedBaseUrl() + path))
                                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                                .header("Authorization", basicAuthorization())
                                .header("Accept", "application/json");
                if (refundIdempotency != null)
                    builder.header("X-Refund-Idempotency", refundIdempotency);
                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(
                                    method,
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)));
                }
                HttpResponse<String> response =
                        httpClient.send(
                                builder.build(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String providerMessage = safeProviderMessage(response.body());
                    boolean retryable =
                            response.statusCode() == 429 || response.statusCode() >= 500;
                    log.warn(
                            "Razorpay API rejected request: path={}, status={}, retryable={},"
                                    + " providerMessage={}",
                            path,
                            response.statusCode(),
                            retryable,
                            providerMessage);
                    throw new PaymentGatewayException(
                            "RAZORPAY_REQUEST_REJECTED",
                            retryable
                                    ? "The payment service is temporarily unavailable. Please try"
                                            + " again."
                                    : "Razorpay could not process this payment request.",
                            retryable);
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
                        exception);
            } catch (IOException | IllegalArgumentException exception) {
                throw new PaymentGatewayException(
                        "RAZORPAY_CONNECTION_FAILED",
                        "Unable to reach the payment service. Please try again.",
                        true,
                        exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "send(String,String,JsonNode,String)");
        }
    }

    /**
     * Maps payment.
     *
     * @param node the node
     * @return the map payment result
     */
    private ProviderPayment mapPayment(JsonNode node) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "mapPayment(JsonNode)");
        try {
            return new ProviderPayment(
                    requiredText(node, "id"),
                    textOrNull(node, "order_id"),
                    requiredText(node, "status"),
                    node.path("amount").asLong(),
                    textOrNull(node, "error_description"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "mapPayment(JsonNode)");
        }
    }

    /**
     * Maps refund.
     *
     * @param node the node
     * @return the map refund result
     */
    private ProviderRefund mapRefund(JsonNode node) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "mapRefund(JsonNode)");
        try {
            return new ProviderRefund(
                    requiredText(node, "id"),
                    requiredText(node, "status"),
                    requiredText(node, "payment_id"),
                    requiredRefundAmount(node));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "mapRefund(JsonNode)");
        }
    }

    /**
     * Requireds refund amount.
     *
     * @param node the node
     * @return the required refund amount result
     */
    private long requiredRefundAmount(JsonNode node) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "requiredRefundAmount(JsonNode)");
        try {
            JsonNode amount = node.path("amount");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() <= 0) {
                throw new IllegalStateException("Razorpay refund amount is missing or invalid.");
            }
            return amount.asLong();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "requiredRefundAmount(JsonNode)");
        }
    }

    /**
     * Verify hmac.
     *
     * @param payload the payload
     * @param suppliedSignature the supplied signature
     * @param secret the secret
     * @param errorCode the error code
     */
    private void verifyHmac(
            byte[] payload, String suppliedSignature, String secret, String errorCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "verifyHmac(byte[],String,String,String)");
        try {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(
                        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
                byte[] expected = mac.doFinal(payload);
                byte[] supplied;
                try {
                    supplied = HexFormat.of().parseHex(suppliedSignature.trim());
                } catch (IllegalArgumentException exception) {
                    throw new PaymentSignatureException(
                            errorCode, "Payment verification signature is invalid.");
                }
                if (!MessageDigest.isEqual(expected, supplied)) {
                    throw new PaymentSignatureException(
                            errorCode, "Payment verification signature is invalid.");
                }
            } catch (PaymentSignatureException exception) {
                throw exception;
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "verifyHmac(byte[],String,String,String)");
        }
    }

    /**
     * Tos subunits.
     *
     * @param amount the amount
     * @return the to subunits result
     */
    private long toSubunits(BigDecimal amount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "toSubunits(BigDecimal)");
        try {
            try {
                return amount.setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Payment amount has an unsupported precision.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "toSubunits(BigDecimal)");
        }
    }

    /**
     * Basics authorization.
     *
     * @return the basic authorization result
     */
    private String basicAuthorization() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "basicAuthorization()");
        try {
            String credentials = properties.getKeyId() + ":" + properties.getKeySecret();
            return "Basic "
                    + Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "basicAuthorization()");
        }
    }

    /**
     * Normalizeds base url.
     *
     * @return the normalized base url result
     */
    private String normalizedBaseUrl() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "normalizedBaseUrl()");
        try {
            return properties.getBaseUrl().replaceAll("/+$", "");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "normalizedBaseUrl()");
        }
    }

    /**
     * Encodes path.
     *
     * @param value the value
     * @return the encode path result
     */
    private String encodePath(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "encodePath(String)");
        try {
            if (value == null || !value.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("Provider identifier is invalid.");
            }
            return value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "encodePath(String)");
        }
    }

    /**
     * Requireds text.
     *
     * @param node the node
     * @param field the field
     * @return the required text result
     */
    private String requiredText(JsonNode node, String field) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "requiredText(JsonNode,String)");
        try {
            String value = textOrNull(node, field);
            if (value == null) {
                throw new PaymentGatewayException(
                        "INVALID_RAZORPAY_RESPONSE",
                        "Razorpay returned an incomplete response.",
                        true);
            }
            return value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayClient.class,
                    "requiredText(JsonNode,String)");
        }
    }

    /**
     * Texts or null.
     *
     * @param node the node
     * @param field the field
     * @return the text or null result
     */
    private String textOrNull(JsonNode node, String field) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "textOrNull(JsonNode,String)");
        try {
            JsonNode value = node.path(field);
            return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "textOrNull(JsonNode,String)");
        }
    }

    /**
     * Safes provider message.
     *
     * @param body the body
     * @return the safe provider message result
     */
    private String safeProviderMessage(String body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "safeProviderMessage(String)");
        try {
            try {
                JsonNode root = objectMapper.readTree(body);
                String description = root.path("error").path("description").asText("");
                return description.length() > 200 ? description.substring(0, 200) : description;
            } catch (RuntimeException ignored) {
                return "unavailable";
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "safeProviderMessage(String)");
        }
    }

    /**
     * Requires signature.
     *
     * @param signature the signature
     */
    private void requireSignature(String signature) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayClient.class, "requireSignature(String)");
        try {
            if (signature == null || signature.isBlank()) {
                throw new PaymentSignatureException(
                        "PAYMENT_SIGNATURE_REQUIRED",
                        "Payment verification signature is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayClient.class, "requireSignature(String)");
        }
    }

    /**
     * Immutable created order data contract.
     *
     * @param id the id
     * @param status the status
     * @param amount the amount
     */
    public record CreatedOrder(String id, String status, long amount) {}

    /**
     * Immutable provider payment data contract.
     *
     * @param id the id
     * @param orderId the order id
     * @param status the status
     * @param amount the amount
     * @param failureReason the failure reason
     */
    public record ProviderPayment(
            String id, String orderId, String status, long amount, String failureReason) {}

    /**
     * Immutable provider refund data contract.
     *
     * @param id the id
     * @param status the status
     * @param paymentId the payment id
     * @param amount the amount
     */
    public record ProviderRefund(String id, String status, String paymentId, long amount) {}
}
