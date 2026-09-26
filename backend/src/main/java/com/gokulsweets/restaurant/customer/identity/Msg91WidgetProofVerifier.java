package com.gokulsweets.restaurant.customer.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
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
public class Msg91WidgetProofVerifier {
    private static final URI VERIFY_URL = URI.create("https://api.msg91.com/api/v5/widget/verifyAccessToken");
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
                throw new IllegalStateException("MSG91 verification failed");
            }
            return verifiedPhoneFromResponse(mapper.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MSG91 verification was interrupted", e);
        } catch (Exception e) {
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
        if (!identifier.matches("91[6-9][0-9]{9}")) {
            throw new IllegalStateException("MSG91 did not return a verified Indian mobile");
        }
        return "+" + identifier;
    }
}
