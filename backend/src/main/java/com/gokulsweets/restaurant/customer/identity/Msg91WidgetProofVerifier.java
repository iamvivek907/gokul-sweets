package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** Server-only verification of an MSG91 widget proof; never trust a client phone claim. */
@Service
@RequiredArgsConstructor
@Slf4j
public class Msg91WidgetProofVerifier {

    private static final URI VERIFY_URL =
            URI.create("https://control.msg91.com/api/v5/widget/verifyAccessToken");

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final EnhancementProperties features;

    private final Environment environment;

    private final ObjectMapper mapper;

    /**
     * Verifieds phone.
     *
     * @param accessToken the access token
     * @return the verified phone result
     */
    public String verifiedPhone(String accessToken) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Msg91WidgetProofVerifier.class, "verifiedPhone(String)");
        try {
            if (!features.isCustomerOtpIdentity()) {
                throw new IllegalStateException("Customer identity is unavailable");
            }
            var authkey = environment.getProperty("gokul.msg91.server-authkey", "");
            if (authkey.isBlank()
                    || accessToken == null
                    || accessToken.isBlank()
                    || accessToken.length() > 4096) {
                log.warn(
                        "MSG91 verification unavailable: missing server configuration or invalid"
                                + " proof input");
                throw new IllegalStateException("Customer identity verification is unavailable");
            }
            try {
                var body =
                        mapper.writeValueAsString(
                                Map.of("authkey", authkey, "access-token", accessToken));
                var request =
                        HttpRequest.newBuilder(VERIFY_URL)
                                .timeout(Duration.ofSeconds(5))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn(
                            "MSG91 verification unavailable: provider HTTP status {}",
                            response.statusCode());
                    throw new IllegalStateException("MSG91 verification failed");
                }
                var providerResponse = mapper.readTree(response.body());
                try {
                    return verifiedPhoneFromResponse(providerResponse);
                } catch (IllegalStateException e) {
                    // These are fixed local categories. Never log the provider body, token,
                    // identifier, or a provider-supplied status/message.
                    var category =
                            switch (e.getMessage()) {
                                case "MSG91 did not verify the token" ->
                                        "provider did not report success";
                                case "MSG91 did not return a verified mobile" ->
                                        "success without mobile field";
                                case "MSG91 did not return a verified Indian mobile" ->
                                        "success without supported mobile format";
                                default -> "unrecognized provider response";
                            };
                    // Log only fixed field-presence flags. Provider values may contain tokens,
                    // phone numbers, or other personal information.
                    var data = providerResponse.path("data");
                    log.warn(
                            "MSG91 verification unavailable: {}; providerType={},"
                                    + " providerReason={}, dataObject={}, identifier={}, mobile={}",
                            category,
                            providerType(providerResponse),
                            providerReason(providerResponse),
                            data.isObject(),
                            data.hasNonNull("identifier"),
                            data.hasNonNull("mobile"));
                    log.warn(
                            "MSG91 verification input shape: proofJwt={}, serverAuthkeyJwt={},"
                                    + " messageText={}, errorText={}",
                            looksLikeJwt(accessToken),
                            looksLikeJwt(authkey),
                            providerResponse.path("message").isTextual(),
                            providerResponse.path("error").isTextual());
                    log.warn(
                            "MSG91 success identity shape: rootIdentifier={}, rootMobile={},"
                                    + " messageMobile={}",
                            providerResponse.hasNonNull("identifier"),
                            providerResponse.hasNonNull("mobile"),
                            isIndianMobile(providerResponse.path("message").asText("")));
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("MSG91 verification unavailable: interrupted");
                throw new IllegalStateException("MSG91 verification was interrupted", e);
            } catch (Exception e) {
                if (!(e instanceof IllegalStateException)) {
                    log.warn(
                            "MSG91 verification unavailable: request or response processing ({})",
                            e.getClass().getSimpleName());
                }
                throw new IllegalStateException("MSG91 verification failed", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "verifiedPhone(String)");
        }
    }

    /**
     * Lookses like jwt.
     *
     * @param value the value
     * @return the looks like jwt result
     */
    static boolean looksLikeJwt(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Msg91WidgetProofVerifier.class, "looksLikeJwt(String)");
        try {
            if (value == null) return false;
            return value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "looksLikeJwt(String)");
        }
    }

    /**
     * Providers type.
     *
     * @param response the response
     * @return the provider type result
     */
    static String providerType(JsonNode response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Msg91WidgetProofVerifier.class, "providerType(JsonNode)");
        try {
            var type = response.path("type").asText("");
            if ("success".equalsIgnoreCase(type)) return "success";
            if ("error".equalsIgnoreCase(type)) return "error";
            if ("failure".equalsIgnoreCase(type)) return "failure";
            return "other";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "providerType(JsonNode)");
        }
    }

    /**
     * Providers reason.
     *
     * @param response the response
     * @return the provider reason result
     */
    static String providerReason(JsonNode response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Msg91WidgetProofVerifier.class, "providerReason(JsonNode)");
        try {
            var message = response.path("message").asText("").toLowerCase(Locale.ROOT);
            if (message.contains("authkey")
                    || message.contains("auth key")
                    || message.contains("authentication failure")) return "authkey";
            if (message.contains("token") && message.contains("expir")) return "expired-token";
            if (message.contains("token")
                    && (message.contains("invalid") || message.contains("incorrect")))
                return "invalid-token";
            if (message.contains("rate limit") || message.contains("too many")) return "rate-limit";
            return response.hasNonNull("message") ? "other" : "absent";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "providerReason(JsonNode)");
        }
    }

    /**
     * Reports whether indian mobile.
     *
     * @param value the value
     * @return the is indian mobile result
     */
    private static boolean isIndianMobile(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(Msg91WidgetProofVerifier.class, "isIndianMobile(String)");
        try {
            return value != null && value.matches("91[6-9][0-9]{9}");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "isIndianMobile(String)");
        }
    }

    /**
     * Verifieds phone from response.
     *
     * @param response the response
     * @return the verified phone from response result
     */
    static String verifiedPhoneFromResponse(JsonNode response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        Msg91WidgetProofVerifier.class, "verifiedPhoneFromResponse(JsonNode)");
        try {
            // Fail closed on unrecognised provider responses until the live contract
            // has been checked with a safe DEV account. Never accept the client phone.
            if (response == null || !"success".equalsIgnoreCase(response.path("type").asText())) {
                throw new IllegalStateException("MSG91 did not verify the token");
            }
            var data = response.path("data");
            var identifier = data.path("identifier").asText("");
            if (identifier.isEmpty()) identifier = data.path("mobile").asText("");
            if (identifier.isEmpty()) identifier = response.path("identifier").asText("");
            if (identifier.isEmpty()) identifier = response.path("mobile").asText("");
            // Some widget verification responses put the verified identifier in
            // message rather than data. Accept it only as the entire mobile string.
            if (identifier.isEmpty() && isIndianMobile(response.path("message").asText(""))) {
                identifier = response.path("message").asText("");
            }
            if (identifier.isEmpty()) {
                throw new IllegalStateException("MSG91 did not return a verified mobile");
            }
            if (!isIndianMobile(identifier)) {
                throw new IllegalStateException("MSG91 did not return a verified Indian mobile");
            }
            return "+" + identifier;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    Msg91WidgetProofVerifier.class,
                    "verifiedPhoneFromResponse(JsonNode)");
        }
    }
}
