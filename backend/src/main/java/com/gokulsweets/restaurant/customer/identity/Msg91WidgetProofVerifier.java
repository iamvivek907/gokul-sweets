package com.gokulsweets.restaurant.customer.identity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Server-only verification of an MSG91 widget proof; never trust a client phone claim. */
@Service
@RequiredArgsConstructor
@Slf4j
public class Msg91WidgetProofVerifier {
    private static final URI VERIFY_URL = URI.create("https://control.msg91.com/api/v5/widget/verifyAccessToken");
    private final EnhancementProperties features;
    private final Environment environment;
    private final ObjectMapper mapper;

    public String verifiedPhone(String accessToken) {
        if (!features.isCustomerOtpIdentity()) {
            throw new IllegalStateException("Customer identity is unavailable");
        }
        var authkey = environment.getProperty("gokul.msg91.server-authkey", "");
        if (authkey.isBlank() || accessToken == null || accessToken.isBlank()
                || accessToken.length() > 4096) {
            log.warn("MSG91 verification unavailable: missing server configuration or invalid proof input");
            throw new IllegalStateException("Customer identity verification is unavailable");
        }
        try {
            var body = mapper.writeValueAsString(Map.of("authkey", authkey, "access-token", accessToken));
            var request = HttpRequest.newBuilder(VERIFY_URL)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                    .build().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("MSG91 verification unavailable: provider HTTP status {}", response.statusCode());
                throw new IllegalStateException("MSG91 verification failed");
            }
            try {
                return verifiedPhoneFromResponse(mapper.readTree(response.body()));
            } catch (IllegalStateException e) {
                // These are fixed local categories. Never log the provider body, token,
                // identifier, or a provider-supplied status/message.
                var category = switch (e.getMessage()) {
                    case "MSG91 did not verify the token" -> "provider did not report success";
                    case "MSG91 did not return a verified mobile" -> "success without mobile field";
                    case "MSG91 did not return a verified Indian mobile" -> "success without supported mobile format";
                    default -> "unrecognized provider response";
                };
                log.warn("MSG91 verification unavailable: {}", category);
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("MSG91 verification unavailable: interrupted");
            throw new IllegalStateException("MSG91 verification was interrupted", e);
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                log.warn("MSG91 verification unavailable: request or response processing ({})",
                        e.getClass().getSimpleName());
            }
            throw new IllegalStateException("MSG91 verification failed", e);
        }
    }

    static String verifiedPhoneFromResponse(JsonNode response) {
        // Fail closed on unrecognised provider responses until the live contract
        // has been checked with a safe DEV account. Never accept the client phone.
        if (response == null || !"success".equalsIgnoreCase(response.path("type").asText())) {
            throw new IllegalStateException("MSG91 did not verify the token");
        }
        var data = response.path("data");
        var identifier = data.path("identifier").asText("");
        if (identifier.isEmpty()) identifier = data.path("mobile").asText("");
        if (identifier.isEmpty()) {
            throw new IllegalStateException("MSG91 did not return a verified mobile");
        }
        if (!identifier.matches("91[6-9][0-9]{9}")) {
            throw new IllegalStateException("MSG91 did not return a verified Indian mobile");
        }
        return "+" + identifier;
    }
}
