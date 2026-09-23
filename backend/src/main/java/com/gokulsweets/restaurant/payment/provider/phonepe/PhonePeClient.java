package com.gokulsweets.restaurant.payment.provider.phonepe;

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

@Component
@Slf4j
public class PhonePeClient {

    private static final String BEARER_PREFIX = "O-Bearer ";

    private final PhonePeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile AccessToken accessToken;

    public PhonePeClient(
            PhonePeProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofSeconds(
                                properties.getConnectTimeoutSeconds()
                        )
                )
                .build();
    }

    /**
     * Creates a PhonePe Standard Checkout V2 order.
     */
    public CreatePaymentResponse createPayment(
            String merchantOrderId,
            BigDecimal amount,
            String redirectUrl
    ) {
        properties.requireApiConfiguration();

        if (merchantOrderId == null || merchantOrderId.isBlank()) {
            throw new IllegalArgumentException(
                    "PhonePe merchant order ID is required."
            );
        }

        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "PhonePe redirect URL is required."
            );
        }

        ObjectNode payload = objectMapper.createObjectNode();

        payload.put(
                "merchantOrderId",
                merchantOrderId
        );

        payload.put(
                "amount",
                toSubunits(amount)
        );

        /*
         * PhonePe allows 300-3600 seconds.
         * 20 minutes is a reasonable checkout lifetime.
         */
        payload.put(
                "expireAfter",
                1200
        );

        ObjectNode paymentFlow = payload.putObject(
                "paymentFlow"
        );

        paymentFlow.put(
                "type",
                "PG_CHECKOUT"
        );

        paymentFlow
                .putObject("merchantUrls")
                .put(
                        "redirectUrl",
                        redirectUrl
                );

        JsonNode response = sendApiRequest(
                "POST",
                properties.getCreatePaymentPath(),
                payload
        );

        String orderId = textOrNull(
                response,
                "orderId"
        );

        String state = textOrNull(
                response,
                "state"
        );

        String responseRedirectUrl = textOrNull(
                response,
                "redirectUrl"
        );

        if (orderId == null) {
            throw new IllegalStateException(
                    "PhonePe did not return an order ID."
            );
        }

        if (responseRedirectUrl == null) {
            throw new IllegalStateException(
                    "PhonePe did not return a checkout redirect URL."
            );
        }

        return new CreatePaymentResponse(
                orderId,
                merchantOrderId,
                state,
                responseRedirectUrl,
                textOrNull(response, "message"),
                longOrNull(response, "expireAt")
        );
    }

    /**
     * Checks current PhonePe order status.
     */
    public StatusResponse checkStatus(
            String merchantOrderId
    ) {
        properties.requireApiConfiguration();

        if (merchantOrderId == null
                || merchantOrderId.isBlank()) {
            throw new IllegalArgumentException(
                    "PhonePe merchant order ID is required."
            );
        }

        String path = properties
                .getOrderStatusPathTemplate()
                .replace(
                        "{merchantOrderId}",
                        URLEncoder.encode(
                                merchantOrderId,
                                StandardCharsets.UTF_8
                        )
                );

        /*
         * details=false means we only need the latest attempt.
         */
        path = normalizedPath(path)
                + "?details=false&errorContext=true";

        JsonNode response = sendApiRequest(
                "GET",
                path,
                null
        );

        String state = textOrNull(
                response,
                "state"
        );

        String transactionId = null;

        JsonNode paymentDetails =
                response.path("paymentDetails");

        if (paymentDetails.isArray()
                && !paymentDetails.isEmpty()) {

            /*
             * PhonePe returns the latest attempt when
             * details=false.
             */
            JsonNode latest =
                    paymentDetails.get(
                            paymentDetails.size() - 1
                    );

            transactionId = textOrNull(
                    latest,
                    "transactionId"
            );
        }

        String errorMessage = null;

        JsonNode errorContext =
                response.path("errorContext");

        if (!errorContext.isMissingNode()) {
            errorMessage = firstNonBlank(
                    textOrNull(errorContext, "errorCode"),
                    textOrNull(errorContext, "detailedErrorCode"),
                    textOrNull(response, "message")
            );
        }

        return new StatusResponse(
                state == null ? "PENDING" : state,
                transactionId,
                errorMessage,
                longOrNull(response, "expireAt")
        );
    }

    /**
     * Verifies the current PhonePe HMAC webhook.
     *
     * PhonePe sends:
     *
     * x-phonepe-checksum-key-id
     * x-phonepe-checksum-signature
     *
     * The configured key ID is checked first, then the raw request body
     * is verified using HMAC-SHA256.
     */
    public void verifyWebhookSignature(
            byte[] rawBody,
            String checksumKeyId,
            String checksumSignature
    ) {
        properties.requireWebhookConfiguration();

        if (rawBody == null) {
            throw new PaymentSignatureException(
                    "PHONEPE_WEBHOOK_BODY_REQUIRED",
                    "PhonePe webhook body is required."
            );
        }

        if (checksumKeyId == null
                || checksumKeyId.isBlank()) {
            throw new PaymentSignatureException(
                    "PHONEPE_WEBHOOK_KEY_ID_REQUIRED",
                    "PhonePe webhook checksum key ID is required."
            );
        }

        if (checksumSignature == null
                || checksumSignature.isBlank()) {
            throw new PaymentSignatureException(
                    "PHONEPE_WEBHOOK_SIGNATURE_REQUIRED",
                    "PhonePe webhook checksum signature is required."
            );
        }

        if (!MessageDigest.isEqual(
                properties
                        .getWebhookChecksumKeyId()
                        .trim()
                        .getBytes(StandardCharsets.UTF_8),
                checksumKeyId
                        .trim()
                        .getBytes(StandardCharsets.UTF_8)
        )) {
            throw new PaymentSignatureException(
                    "INVALID_PHONEPE_WEBHOOK_KEY_ID",
                    "PhonePe webhook checksum key ID is invalid."
            );
        }

        String expected = hmacSha256Hex(
                rawBody,
                properties.getWebhookChecksumSecret()
        );

        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                checksumSignature
                        .trim()
                        .getBytes(StandardCharsets.UTF_8)
        )) {
            throw new PaymentSignatureException(
                    "INVALID_PHONEPE_WEBHOOK_SIGNATURE",
                    "PhonePe webhook checksum signature is invalid."
            );
        }
    }

    private JsonNode sendApiRequest(
            String method,
            String path,
            JsonNode body
    ) {
        String token = getAccessToken();

        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            normalizedBaseUrl()
                                                    + normalizedPath(path)
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(
                                            properties
                                                    .getRequestTimeoutSeconds()
                                    )
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .header(
                                    "Authorization",
                                    BEARER_PREFIX + token
                            );

            if (body == null) {
                builder.header(
                        "Content-Type",
                        "application/json"
                );

                builder.method(
                        method,
                        HttpRequest.BodyPublishers.noBody()
                );
            } else {
                builder.header(
                        "Content-Type",
                        "application/json"
                );

                builder.method(
                        method,
                        HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body)
                        )
                );
            }

            HttpResponse<String> response =
                    httpClient.send(
                            builder.build(),
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            /*
             * Token may have expired between getAccessToken()
             * and the actual API call.
             *
             * Retry exactly once after invalidating it.
             */
            if (response.statusCode() == 401) {
                invalidateToken(token);

                String refreshedToken =
                        getAccessToken();

                return sendApiRequestWithToken(
                        method,
                        path,
                        body,
                        refreshedToken
                );
            }

            return handleApiResponse(response);

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

    private JsonNode sendApiRequestWithToken(
            String method,
            String path,
            JsonNode body,
            String token
    ) {
        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            normalizedBaseUrl()
                                                    + normalizedPath(path)
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(
                                            properties
                                                    .getRequestTimeoutSeconds()
                                    )
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .header(
                                    "Authorization",
                                    BEARER_PREFIX + token
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            );

            if (body == null) {
                builder.method(
                        method,
                        HttpRequest.BodyPublishers.noBody()
                );
            } else {
                builder.method(
                        method,
                        HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body)
                        )
                );
            }

            HttpResponse<String> response =
                    httpClient.send(
                            builder.build(),
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            return handleApiResponse(response);

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

    private JsonNode handleApiResponse(
            HttpResponse<String> response
    ) {
        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            boolean retryable =
                    response.statusCode() == 429
                            || response.statusCode() >= 500;

            String providerMessage =
                    extractProviderMessage(
                            response.body()
                    );

            throw new PaymentGatewayException(
                    "PHONEPE_REQUEST_REJECTED",
                    providerMessage == null
                            ? (
                            retryable
                                    ? "PhonePe is temporarily unavailable. Please try again."
                                    : "PhonePe could not process this payment request."
                    )
                            : providerMessage,
                    retryable
            );
        }

        try {
            return objectMapper.readTree(
                    response.body()
            );
        } catch (Exception exception) {
            throw new PaymentGatewayException(
                    "PHONEPE_INVALID_RESPONSE",
                    "PhonePe returned an invalid response.",
                    true,
                    exception
            );
        }
    }

    /**
     * Gets an OAuth token and caches it until shortly before expiry.
     */
    private String getAccessToken() {
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
                                + urlEncode(
                                properties.getClientId()
                        )
                                + "&client_version="
                                + urlEncode(
                                properties.getClientVersion()
                        )
                                + "&client_secret="
                                + urlEncode(
                                properties.getClientSecret()
                        )
                                + "&grant_type=client_credentials";

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                normalizedAuthorizationBaseUrl()
                                                        + normalizedPath(
                                                        properties
                                                                .getAuthorizationPath()
                                                )
                                        )
                                )
                                .timeout(
                                        Duration.ofSeconds(
                                                properties
                                                        .getRequestTimeoutSeconds()
                                        )
                                )
                                .header(
                                        "Content-Type",
                                        "application/x-www-form-urlencoded"
                                )
                                .header(
                                        "Accept",
                                        "application/json"
                                )
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(form)
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString(
                                        StandardCharsets.UTF_8
                                )
                        );

                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {

                    throw new PaymentGatewayException(
                            "PHONEPE_AUTH_FAILED",
                            "PhonePe authorization failed.",
                            response.statusCode() == 429
                                    || response.statusCode() >= 500
                    );
                }

                JsonNode root =
                        objectMapper.readTree(
                                response.body()
                        );

                String token =
                        textOrNull(
                                root,
                                "access_token"
                        );

                Long expiresAt =
                        longOrNull(
                                root,
                                "expires_at"
                        );

                if (token == null
                        || expiresAt == null) {

                    throw new PaymentGatewayException(
                            "PHONEPE_AUTH_INVALID",
                            "PhonePe authorization response is invalid.",
                            true
                    );
                }

                AccessToken created =
                        new AccessToken(
                                token,
                                Instant.ofEpochSecond(
                                        expiresAt
                                )
                        );

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
                        exception
                );

            } catch (IOException | RuntimeException exception) {
                throw new PaymentGatewayException(
                        "PHONEPE_AUTH_FAILED",
                        "Unable to authenticate with PhonePe.",
                        true,
                        exception
                );
            }

        } finally {
            tokenLock.unlock();
        }
    }

    private void invalidateToken(String token) {
        AccessToken current = accessToken;

        if (current != null
                && current.value().equals(token)) {
            accessToken = null;
        }
    }

    private String extractProviderMessage(
            String responseBody
    ) {
        if (responseBody == null
                || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            return firstNonBlank(
                    textOrNull(root, "message"),
                    textOrNull(root, "code")
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    private String hmacSha256Hex(
            byte[] payload,
            String secret
    ) {
        try {
            Mac mac =
                    Mac.getInstance("HmacSHA256");

            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA256"
                    )
            );

            return HexFormat.of().formatHex(
                    mac.doFinal(payload)
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate PhonePe webhook signature.",
                    exception
            );
        }
    }

    private long toSubunits(
            BigDecimal amount
    ) {
        try {
            return amount
                    .setScale(
                            2,
                            RoundingMode.UNNECESSARY
                    )
                    .movePointRight(2)
                    .longValueExact();

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Payment amount has an unsupported precision.",
                    exception
            );
        }
    }

    private String normalizedBaseUrl() {
        return properties
                .getBaseUrl()
                .replaceAll("/+$", "");
    }

    private String normalizedAuthorizationBaseUrl() {
        return properties
                .getAuthorizationBaseUrl()
                .replaceAll("/+$", "");
    }

    private String normalizedPath(
            String path
    ) {
        String trimmed =
                path == null
                        ? ""
                        : path.trim();

        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }

        return trimmed;
    }

    private String urlEncode(
            String value
    ) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String textOrNull(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isTextual()
                && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private Long longOrNull(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isNumber()
                ? value.longValue()
                : null;
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    public record CreatePaymentResponse(
            String orderId,
            String merchantOrderId,
            String state,
            String redirectUrl,
            String message,
            Long expireAt
    ) {
    }

    public record StatusResponse(
            String state,
            String transactionId,
            String errorMessage,
            Long expireAt
    ) {
    }

    private record AccessToken(
            String value,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return expiresAt.isBefore(
                    Instant.now().plusSeconds(
                            30
                    )
            );
        }
    }
}