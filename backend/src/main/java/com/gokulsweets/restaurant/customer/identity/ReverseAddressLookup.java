package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Backend reverse address lookup contract and implementation. */
@Service
@RequiredArgsConstructor
public class ReverseAddressLookup {

    private final Environment settings;

    private final JdbcTemplate jdbc;

    /**
     * Immutable coordinates data contract.
     *
     * @param latitude the latitude
     * @param longitude the longitude
     */
    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}

    /**
     * Immutable suggestion data contract.
     *
     * @param addressLine the address line
     * @param locality the locality
     * @param postalCode the postal code
     * @param attribution the attribution
     */
    public record Suggestion(
            String addressLine, String locality, String postalCode, String attribution) {}

    /**
     * Returns whether the configured prerequisites for this feature are enabled.
     *
     * @return the {@code boolean} result
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ReverseAddressLookup.class, "enabled()");
        try {
            return !settings.getProperty("gokul.geocoding.api-key", "").isBlank();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ReverseAddressLookup.class, "enabled()");
        }
    }

    /**
     * Suggests reverse address lookup data and returns the {@code Suggestion} result.
     *
     * <p>Writes {@code customer_location_lookup_limits}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @param value the value supplied to this method
     * @return the value of {@code parse(body)}
     * @throws ResponseStatusException when the method rejects the request with {@code Choose valid
     *     coordinates.}; {@code Location address suggestions are not configured. Enter your address
     *     manually.}; {@code Please wait a minute before requesting another location suggestion.};
     *     {@code We could not suggest an address. Enter it manually or try again later.}
     */
    public Suggestion suggest(String environment, UUID subject, Coordinates value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ReverseAddressLookup.class, "suggest(String,UUID,Coordinates)");
        try {
            if (!enabled())
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Location address suggestions are not configured. Enter your address"
                                + " manually.");
            if (value == null
                    || value.latitude() == null
                    || value.longitude() == null
                    || value.latitude().abs().compareTo(new BigDecimal("90")) > 0
                    || value.longitude().abs().compareTo(new BigDecimal("180")) > 0)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose valid coordinates.");
            if (jdbc.update(
                            """
INSERT INTO customer_location_lookup_limits(environment,subject_id,last_requested_at) VALUES (?,?,CURRENT_TIMESTAMP)
ON CONFLICT(environment,subject_id) DO UPDATE SET last_requested_at=CURRENT_TIMESTAMP
WHERE customer_location_lookup_limits.last_requested_at<CURRENT_TIMESTAMP-INTERVAL '1 minute'
""",
                            environment,
                            subject)
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait a minute before requesting another location suggestion.");
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(3));
            factory.setReadTimeout(Duration.ofSeconds(5));
            try {
                String body =
                        RestClient.builder()
                                .requestFactory(factory)
                                .baseUrl("https://maps.googleapis.com")
                                .build()
                                .get()
                                .uri(
                                        uri ->
                                                uri.path("/maps/api/geocode/json")
                                                        .queryParam(
                                                                "latlng",
                                                                value.latitude()
                                                                        + ","
                                                                        + value.longitude())
                                                        .queryParam("language", "en")
                                                        .queryParam(
                                                                "key",
                                                                settings.getProperty(
                                                                        "gokul.geocoding.api-key",
                                                                        ""))
                                                        .build())
                                .retrieve()
                                .body(String.class);
                return parse(body);
            } catch (RuntimeException ignored) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "We could not suggest an address. Enter it manually or try again later.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ReverseAddressLookup.class,
                    "suggest(String,UUID,Coordinates)");
        }
    }

    /**
     * Parses reverse address lookup data and returns the {@code Suggestion} result.
     *
     * @param body the body supplied to this method
     * @return the {@code Suggestion} result
     * @throws IllegalArgumentException when the method rejects the request with {@code No suitable
     *     Indian address suggestion.}
     */
    static Suggestion parse(String body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ReverseAddressLookup.class, "parse(String)");
        try {
            try {
                var root = new tools.jackson.databind.ObjectMapper().readTree(body);
                if (!"OK".equals(root.path("status").asText()) || root.path("results").isEmpty())
                    throw new IllegalArgumentException();
                var first = root.path("results").get(0);
                String locality = "", postal = "", country = "";
                for (var part : first.path("address_components"))
                    for (var type : part.path("types")) {
                        switch (type.asText()) {
                            case "locality" -> locality = part.path("long_name").asText();
                            case "sublocality", "sublocality_level_1" -> {
                                if (locality.isEmpty()) locality = part.path("long_name").asText();
                            }
                            case "postal_code" -> postal = part.path("long_name").asText();
                            case "country" -> country = part.path("short_name").asText();
                            default -> {}
                        }
                    }
                if (!"IN".equals(country)) throw new IllegalArgumentException();
                return new Suggestion(
                        limit(first.path("formatted_address").asText(), 180),
                        limit(locality, 100),
                        postal.matches("[1-9][0-9]{5}") ? postal : "",
                        "Google Maps");
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("No suitable Indian address suggestion.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ReverseAddressLookup.class, "parse(String)");
        }
    }

    /**
     * Returns limit information for reverse address lookup.
     *
     * @param value the value supplied to this method
     * @param length the length supplied to this method
     * @return the value of {@code value.length() > length ? value.substring(0, length) : value}
     */
    private static String limit(String value, int length) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ReverseAddressLookup.class, "limit(String,int)");
        try {
            return value.length() > length ? value.substring(0, length) : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ReverseAddressLookup.class, "limit(String,int)");
        }
    }
}
