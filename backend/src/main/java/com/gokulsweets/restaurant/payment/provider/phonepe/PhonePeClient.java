package com.gokulsweets.restaurant.payment.provider.phonepe;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Backend phone pe client contract and implementation. */
@Component
@Slf4j
public class PhonePeClient {

    private static final String BEARER_PREFIX = AppConstant.PHONE_PE_CLIENT_BEARER_PREFIX;

    private final PhonePeProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile AccessToken accessToken;

    /**
     * Creates a phone pe client instance.
     *
     * @param properties the properties
     * @param objectMapper the object mapper
     */
    public PhonePeClient(PhonePeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                        .build();
    }

    /**
     * Creates a PhonePe Standard Checkout V2 order.
     *
     * @param merchantOrderId the merchant order id
     * @param amount the amount
     * @param redirectUrl the redirect url
     * @return the operation result
     */
    public CreatePaymentResponse createPayment(
            String merchantOrderId, BigDecimal amount, String redirectUrl) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "createPayment(String,BigDecimal,String)");
        try {
            return createPayment(merchantOrderId, amount, redirectUrl, 1200);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "createPayment(String,BigDecimal,String)");
        }
    }

    /**
     * A shorter provider window keeps occasion deposit attempts inside the inventory hold.
     *
     * @param merchantOrderId the merchant order id
     * @param amount the amount
     * @param redirectUrl the redirect url
     * @param expireAfterSeconds the expire after seconds
     * @return the operation result
     */
    public CreatePaymentResponse createPayment(
            String merchantOrderId, BigDecimal amount, String redirectUrl, int expireAfterSeconds) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePeClient.class, "createPayment(String,BigDecimal,String,int)");
        try {
            properties.requireApiConfiguration();
            if (merchantOrderId == null || merchantOrderId.isBlank()) {
                throw new IllegalArgumentException("PhonePe merchant order ID is required.");
            }
            if (redirectUrl == null || redirectUrl.isBlank()) {
                throw new IllegalArgumentException("PhonePe redirect URL is required.");
            }
            if (expireAfterSeconds < 300 || expireAfterSeconds > 3600) {
                throw new IllegalArgumentException(
                        "Payment expiry must be between 300 and 3600 seconds.");
            }
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("merchantOrderId", merchantOrderId);
            payload.put("amount", toSubunits(amount));
            /*
             * PhonePe allows 300-3600 seconds.
             * 20 minutes is a reasonable checkout lifetime.
             */
            payload.put("expireAfter", expireAfterSeconds);
            ObjectNode paymentFlow = payload.putObject("paymentFlow");
            paymentFlow.put("type", "PG_CHECKOUT");
            paymentFlow.putObject("merchantUrls").put("redirectUrl", redirectUrl);
            JsonNode response = sendApiRequest("POST", properties.getCreatePaymentPath(), payload);
            String orderId = textOrNull(response, "orderId");
            String state = textOrNull(response, "state");
            String responseRedirectUrl = textOrNull(response, "redirectUrl");
            if (orderId == null) {
                throw new IllegalStateException("PhonePe did not return an order ID.");
            }
            if (responseRedirectUrl == null) {
                throw new IllegalStateException("PhonePe did not return a checkout redirect URL.");
            }
            return new CreatePaymentResponse(
                    orderId,
                    merchantOrderId,
                    state,
                    responseRedirectUrl,
                    textOrNull(response, "message"),
                    longOrNull(response, "expireAt"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "createPayment(String,BigDecimal,String,int)");
        }
    }

    /**
     * Checks current PhonePe order status.
     *
     * @param merchantOrderId the merchant order id
     * @return the operation result
     */
    public StatusResponse checkStatus(String merchantOrderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "checkStatus(String)");
        try {
            properties.requireApiConfiguration();
            if (merchantOrderId == null || merchantOrderId.isBlank()) {
                throw new IllegalArgumentException("PhonePe merchant order ID is required.");
            }
            String path =
                    properties
                            .getOrderStatusPathTemplate()
                            .replace(
                                    "{merchantOrderId}",
                                    URLEncoder.encode(merchantOrderId, StandardCharsets.UTF_8));
            /*
             * details=false means we only need the latest attempt.
             */
            path = normalizedPath(path) + "?details=false&errorContext=true";
            JsonNode response = sendApiRequest("GET", path, null);
            String state = textOrNull(response, "state");
            String transactionId = null;
            JsonNode paymentDetails = response.path("paymentDetails");
            if (paymentDetails.isArray() && !paymentDetails.isEmpty()) {
                /*
                 * PhonePe returns the latest attempt when
                 * details=false.
                 */
                JsonNode latest = paymentDetails.get(paymentDetails.size() - 1);
                transactionId = textOrNull(latest, "transactionId");
            }
            String errorMessage = null;
            JsonNode errorContext = response.path("errorContext");
            if (!errorContext.isMissingNode()) {
                errorMessage =
                        firstNonBlank(
                                textOrNull(errorContext, "errorCode"),
                                textOrNull(errorContext, "detailedErrorCode"),
                                textOrNull(response, "message"));
            }
            return new StatusResponse(
                    state == null ? "PENDING" : state,
                    transactionId,
                    errorMessage,
                    longOrNull(response, "expireAt"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "checkStatus(String)");
        }
    }

    /**
     * Verifies the current PhonePe HMAC webhook.
     *
     * <p>PhonePe sends:
     *
     * <p>x-phonepe-checksum-key-id x-phonepe-checksum-signature
     *
     * <p>The configured key ID is checked first, then the raw request body is verified using
     * HMAC-SHA256.
     *
     * @param rawBody the raw body
     * @param checksumKeyId the checksum key id
     * @param checksumSignature the checksum signature
     */
    public void verifyWebhookSignature(
            byte[] rawBody, String checksumKeyId, String checksumSignature) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePeClient.class, "verifyWebhookSignature(byte[],String,String)");
        try {
            properties.requireWebhookConfiguration();
            if (rawBody == null) {
                throw new PaymentSignatureException(
                        "PHONEPE_WEBHOOK_BODY_REQUIRED", "PhonePe webhook body is required.");
            }
            if (checksumKeyId == null || checksumKeyId.isBlank()) {
                throw new PaymentSignatureException(
                        "PHONEPE_WEBHOOK_KEY_ID_REQUIRED",
                        "PhonePe webhook checksum key ID is required.");
            }
            if (checksumSignature == null || checksumSignature.isBlank()) {
                throw new PaymentSignatureException(
                        "PHONEPE_WEBHOOK_SIGNATURE_REQUIRED",
                        "PhonePe webhook checksum signature is required.");
            }
            if (!MessageDigest.isEqual(
                    properties.getWebhookChecksumKeyId().trim().getBytes(StandardCharsets.UTF_8),
                    checksumKeyId.trim().getBytes(StandardCharsets.UTF_8))) {
                throw new PaymentSignatureException(
                        "INVALID_PHONEPE_WEBHOOK_KEY_ID",
                        "PhonePe webhook checksum key ID is invalid.");
            }
            String expected = hmacSha256Hex(rawBody, properties.getWebhookChecksumSecret());
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    checksumSignature.trim().getBytes(StandardCharsets.UTF_8))) {
                throw new PaymentSignatureException(
                        "INVALID_PHONEPE_WEBHOOK_SIGNATURE",
                        "PhonePe webhook checksum signature is invalid.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "verifyWebhookSignature(byte[],String,String)");
        }
    }

    /**
     * Merchant refund reference and amount remain identical on every recovery attempt.
     *
     * @param refundId the refund id
     * @param amount the amount
     * @param state the state
     * @param merchantRefundId the merchant refund id
     * @param originalMerchantOrderId the original merchant order id
     */
    public record RefundResponse(
            String refundId,
            long amount,
            String state,
            String merchantRefundId,
            String originalMerchantOrderId) {}

    /**
     * Refunds phone pe data and returns the {@code RefundResponse} result.
     *
     * @param reference the reference supplied to this method
     * @param orderId the order id supplied to this method
     * @param amount the amount supplied to this method
     * @return the {@code RefundResponse} result
     */
    public RefundResponse refund(String reference, String orderId, BigDecimal amount) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "refund(String,String,BigDecimal)");
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("merchantRefundId", reference);
            payload.put("originalMerchantOrderId", orderId);
            payload.put("amount", toSubunits(amount));
            return refundResponse(sendApiRequest("POST", "/payments/v2/refund", payload));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "refund(String,String,BigDecimal)");
        }
    }

    /**
     * Refunds status.
     *
     * @param reference the reference
     * @return the refund status result
     */
    public RefundResponse refundStatus(String reference) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "refundStatus(String)");
        try {
            return refundResponse(
                    sendApiRequest(
                            "GET",
                            "/payments/v2/refund/"
                                    + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                                    + "/status",
                            null));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "refundStatus(String)");
        }
    }

    /**
     * Refunds response.
     *
     * @param value the value
     * @return the refund response result
     */
    public RefundResponse refundResponse(JsonNode value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "refundResponse(JsonNode)");
        try {
            if (value == null
                    || !value.isObject()
                    || !value.path("amount").isIntegralNumber()
                    || !value.path("amount").canConvertToLong()
                    || value.path("amount").asLong() <= 0
                    || !value.path("state").isTextual()
                    || textOrNull(value, "state") == null) {
                // Describe only known field types; never log arbitrary response values or bodies.
                log.warn(
                        "PhonePe refund response schema mismatch: object={}, amountIntegral={},"
                                + " stateText={}, refundIdText={}",
                        value != null && value.isObject(),
                        value != null && value.path("amount").isIntegralNumber(),
                        value != null && value.path("state").isTextual(),
                        value != null && value.path("refundId").isTextual());
                throw new PaymentGatewayException(
                        "PHONEPE_REFUND_INVALID_RESPONSE",
                        "PhonePe did not return a complete refund response.",
                        true);
            }
            return new RefundResponse(
                    textOrNull(value, "refundId"),
                    value.path("amount").asLong(),
                    textOrNull(value, "state"),
                    textOrNull(value, "merchantRefundId"),
                    textOrNull(value, "originalMerchantOrderId"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "refundResponse(JsonNode)");
        }
    }

    /**
     * Sends api request.
     *
     * @param method the method
     * @param path the path
     * @param body the body
     * @return the send api request result
     */
    private JsonNode sendApiRequest(String method, String path, JsonNode body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "sendApiRequest(String,String,JsonNode)");
        try {
            String token = getAccessToken();
            try {
                HttpRequest.Builder builder =
                        HttpRequest.newBuilder()
                                .uri(URI.create(normalizedBaseUrl() + normalizedPath(path)))
                                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                                .header("Accept", "application/json")
                                .header("Authorization", BEARER_PREFIX + token);
                if (body == null) {
                    builder.header("Content-Type", "application/json");
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json");
                    builder.method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(body)));
                }
                HttpResponse<String> response =
                        httpClient.send(
                                builder.build(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                /*
                 * Token may have expired between getAccessToken()
                 * and the actual API call.
                 *
                 * Retry exactly once after invalidating it.
                 */
                if (response.statusCode() == 401) {
                    invalidateToken(token);
                    String refreshedToken = getAccessToken();
                    return sendApiRequestWithToken(method, path, body, refreshedToken);
                }
                return handleApiResponse(response, method, path);
            } catch (PaymentGatewayException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PaymentGatewayException(
                        "PHONEPE_REQUEST_INTERRUPTED",
                        "PhonePe payment request was interrupted. Please try again.",
                        true,
                        exception);
            } catch (IOException | RuntimeException exception) {
                throw new PaymentGatewayException(
                        "PHONEPE_CONNECTION_FAILED",
                        "Unable to reach PhonePe right now. Please try again.",
                        true,
                        exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "sendApiRequest(String,String,JsonNode)");
        }
    }

    /**
     * Sends api request with token.
     *
     * @param method the method
     * @param path the path
     * @param body the body
     * @param token the token
     * @return the send api request with token result
     */
    private JsonNode sendApiRequestWithToken(
            String method, String path, JsonNode body, String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePeClient.class,
                        "sendApiRequestWithToken(String,String,JsonNode,String)");
        try {
            try {
                HttpRequest.Builder builder =
                        HttpRequest.newBuilder()
                                .uri(URI.create(normalizedBaseUrl() + normalizedPath(path)))
                                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                                .header("Accept", "application/json")
                                .header("Authorization", BEARER_PREFIX + token)
                                .header("Content-Type", "application/json");
                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(body)));
                }
                HttpResponse<String> response =
                        httpClient.send(
                                builder.build(),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return handleApiResponse(response, method, path);
            } catch (PaymentGatewayException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PaymentGatewayException(
                        "PHONEPE_REQUEST_INTERRUPTED",
                        "PhonePe payment request was interrupted. Please try again.",
                        true,
                        exception);
            } catch (IOException | RuntimeException exception) {
                throw new PaymentGatewayException(
                        "PHONEPE_CONNECTION_FAILED",
                        "Unable to reach PhonePe right now. Please try again.",
                        true,
                        exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "sendApiRequestWithToken(String,String,JsonNode,String)");
        }
    }

    /**
     * Handles api response.
     *
     * @param response the response
     * @param method the method
     * @param path the path
     * @return the handle api response result
     */
    private JsonNode handleApiResponse(HttpResponse<String> response, String method, String path) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePeClient.class,
                        "handleApiResponse(HttpResponse<String>,String,String)");
        try {
            if (path.startsWith("/payments/v2/refund")) {
                log.info(
                        "PhonePe refund API response: operation={}, httpStatus={}, providerCode={}",
                        "GET".equals(method) ? "STATUS" : "SUBMIT",
                        response.statusCode(),
                        safeProviderCode(response.body()));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                String providerMessage = extractProviderMessage(response.body());
                throw new PaymentGatewayException(
                        "GET".equals(method)
                                        && path.startsWith("/payments/v2/refund/")
                                        && path.endsWith("/status")
                                        && refundNotFound(response)
                                ? "PHONEPE_REFUND_NOT_FOUND"
                                : "PHONEPE_REQUEST_REJECTED",
                        providerMessage == null
                                ? (retryable
                                        ? "PhonePe is temporarily unavailable. Please try again."
                                        : "PhonePe could not process this payment request.")
                                : providerMessage,
                        retryable);
            }
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception exception) {
                throw new PaymentGatewayException(
                        "PHONEPE_INVALID_RESPONSE",
                        "PhonePe returned an invalid response.",
                        true,
                        exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "handleApiResponse(HttpResponse<String>,String,String)");
        }
    }

    /**
     * Gets an OAuth token and caches it until shortly before expiry.
     *
     * @return the operation result
     */
    private String getAccessToken() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "getAccessToken()");
        try {
            AccessToken current = accessToken;
            if (current != null && !current.isExpired()) {
                return current.value();
            }
            tokenLock.lock();
            try {
                current = accessToken;
                if (current != null && !current.isExpired()) {
                    return current.value();
                }
                properties.requireApiConfiguration();
                try {
                    String form =
                            "client_id="
                                    + urlEncode(properties.getClientId())
                                    + "&client_version="
                                    + urlEncode(properties.getClientVersion())
                                    + "&client_secret="
                                    + urlEncode(properties.getClientSecret())
                                    + "&grant_type=client_credentials";
                    HttpRequest request =
                            HttpRequest.newBuilder()
                                    .uri(
                                            URI.create(
                                                    normalizedAuthorizationBaseUrl()
                                                            + normalizedPath(
                                                                    properties
                                                                            .getAuthorizationPath())))
                                    .timeout(
                                            Duration.ofSeconds(
                                                    properties.getRequestTimeoutSeconds()))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .header("Accept", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(form))
                                    .build();
                    HttpResponse<String> response =
                            httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new PaymentGatewayException(
                                "PHONEPE_AUTH_FAILED",
                                "PhonePe authorization failed.",
                                response.statusCode() == 429 || response.statusCode() >= 500);
                    }
                    JsonNode root = objectMapper.readTree(response.body());
                    String token = textOrNull(root, "access_token");
                    Long expiresAt = longOrNull(root, "expires_at");
                    if (token == null || expiresAt == null) {
                        throw new PaymentGatewayException(
                                "PHONEPE_AUTH_INVALID",
                                "PhonePe authorization response is invalid.",
                                true);
                    }
                    AccessToken created = new AccessToken(token, Instant.ofEpochSecond(expiresAt));
                    accessToken = created;
                    return created.value();
                } catch (PaymentGatewayException exception) {
                    throw exception;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new PaymentGatewayException(
                            "PHONEPE_AUTH_INTERRUPTED",
                            "PhonePe authorization was interrupted.",
                            true,
                            exception);
                } catch (IOException | RuntimeException exception) {
                    throw new PaymentGatewayException(
                            "PHONEPE_AUTH_FAILED",
                            "Unable to authenticate with PhonePe.",
                            true,
                            exception);
                }
            } finally {
                tokenLock.unlock();
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PhonePeClient.class, "getAccessToken()");
        }
    }

    /**
     * Invalidates token.
     *
     * @param token the token
     */
    private void invalidateToken(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "invalidateToken(String)");
        try {
            AccessToken current = accessToken;
            if (current != null && current.value().equals(token)) {
                accessToken = null;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "invalidateToken(String)");
        }
    }

    /**
     * Safes provider code.
     *
     * @param body the body
     * @return the safe provider code result
     */
    private String safeProviderCode(String body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "safeProviderCode(String)");
        try {
            try {
                String code = textOrNull(objectMapper.readTree(body), "code");
                if (code == null) return "NONE";
                return code.matches("[A-Z][A-Z0-9_]{0,63}") ? code : "UNRECOGNIZED";
            } catch (Exception ignored) {
                return "UNREADABLE";
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "safeProviderCode(String)");
        }
    }

    /**
     * Refunds not found.
     *
     * @param response the response
     * @return the refund not found result
     */
    private boolean refundNotFound(HttpResponse<String> response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "refundNotFound(HttpResponse<String>)");
        try {
            if (response.statusCode() != 404) return false;
            try {
                return "REFUND_NOT_FOUND"
                        .equals(textOrNull(objectMapper.readTree(response.body()), "code"));
            } catch (Exception ignored) {
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "refundNotFound(HttpResponse<String>)");
        }
    }

    /**
     * Extracts provider message.
     *
     * @param responseBody the response body
     * @return the extract provider message result
     */
    private String extractProviderMessage(String responseBody) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "extractProviderMessage(String)");
        try {
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                return firstNonBlank(textOrNull(root, "message"), textOrNull(root, "code"));
            } catch (Exception ignored) {
                return null;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "extractProviderMessage(String)");
        }
    }

    /**
     * Hmacs sha256 hex.
     *
     * @param payload the payload
     * @param secret the secret
     * @return the hmac sha256 hex result
     */
    private String hmacSha256Hex(byte[] payload, String secret) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "hmacSha256Hex(byte[],String)");
        try {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(payload));
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Unable to calculate PhonePe webhook signature.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "hmacSha256Hex(byte[],String)");
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
                MethodTiming.start(PhonePeClient.class, "toSubunits(BigDecimal)");
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
                    __gokulMethodStartedNanos, PhonePeClient.class, "toSubunits(BigDecimal)");
        }
    }

    /**
     * Normalizeds base url.
     *
     * @return the normalized base url result
     */
    private String normalizedBaseUrl() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "normalizedBaseUrl()");
        try {
            return properties.getBaseUrl().replaceAll("/+$", "");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "normalizedBaseUrl()");
        }
    }

    /**
     * Normalizeds authorization base url.
     *
     * @return the normalized authorization base url result
     */
    private String normalizedAuthorizationBaseUrl() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "normalizedAuthorizationBaseUrl()");
        try {
            return properties.getAuthorizationBaseUrl().replaceAll("/+$", "");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeClient.class,
                    "normalizedAuthorizationBaseUrl()");
        }
    }

    /**
     * Normalizeds path.
     *
     * @param path the path
     * @return the normalized path result
     */
    private String normalizedPath(String path) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "normalizedPath(String)");
        try {
            String trimmed = path == null ? "" : path.trim();
            if (!trimmed.startsWith("/")) {
                trimmed = "/" + trimmed;
            }
            return trimmed;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "normalizedPath(String)");
        }
    }

    /**
     * Urls encode.
     *
     * @param value the value
     * @return the url encode result
     */
    private String urlEncode(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "urlEncode(String)");
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "urlEncode(String)");
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
                MethodTiming.start(PhonePeClient.class, "textOrNull(JsonNode,String)");
        try {
            JsonNode value = node.path(field);
            return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "textOrNull(JsonNode,String)");
        }
    }

    /**
     * Longs or null.
     *
     * @param node the node
     * @param field the field
     * @return the long or null result
     */
    private Long longOrNull(JsonNode node, String field) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "longOrNull(JsonNode,String)");
        try {
            JsonNode value = node.path(field);
            return value.isNumber() ? value.longValue() : null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "longOrNull(JsonNode,String)");
        }
    }

    /**
     * Firsts non blank.
     *
     * @param values the values
     * @return the first non blank result
     */
    private String firstNonBlank(String... values) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeClient.class, "firstNonBlank(String...)");
        try {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePeClient.class, "firstNonBlank(String...)");
        }
    }

    /**
     * Immutable create payment response data contract.
     *
     * @param orderId the order id
     * @param merchantOrderId the merchant order id
     * @param state the state
     * @param redirectUrl the redirect url
     * @param message the message
     * @param expireAt the expire at
     */
    public record CreatePaymentResponse(
            String orderId,
            String merchantOrderId,
            String state,
            String redirectUrl,
            String message,
            Long expireAt) {}

    /**
     * Immutable status response data contract.
     *
     * @param state the state
     * @param transactionId the transaction id
     * @param errorMessage the error message
     * @param expireAt the expire at
     */
    public record StatusResponse(
            String state, String transactionId, String errorMessage, Long expireAt) {}

    /**
     * Immutable access token data contract.
     *
     * @param value the value
     * @param expiresAt the expires at
     */
    private record AccessToken(String value, Instant expiresAt) {

        /**
         * Reports whether expired.
         *
         * @return the is expired result
         */
        boolean isExpired() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(PhonePeClient.AccessToken.class, "isExpired()");
            try {
                return expiresAt.isBefore(Instant.now().plusSeconds(30));
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, PhonePeClient.AccessToken.class, "isExpired()");
            }
        }
    }
}
