package com.gokulsweets.restaurant.customer.identity;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReverseAddressLookup {
  private final Environment settings;
  private final JdbcTemplate jdbc;

  public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}

  public record Suggestion(
      String addressLine, String locality, String postalCode, String attribution) {}

  public boolean enabled() {
    return !settings.getProperty("gokul.geocoding.api-key", "").isBlank();
  }

  public Suggestion suggest(String environment, UUID subject, Coordinates value) {
    if (!enabled())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Location address suggestions are not configured. Enter your address manually.");
    if (value == null
        || value.latitude() == null
        || value.longitude() == null
        || value.latitude().abs().compareTo(new BigDecimal("90")) > 0
        || value.longitude().abs().compareTo(new BigDecimal("180")) > 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose valid coordinates.");
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
                          .queryParam("latlng", value.latitude() + "," + value.longitude())
                          .queryParam("language", "en")
                          .queryParam("key", settings.getProperty("gokul.geocoding.api-key", ""))
                          .build())
              .retrieve()
              .body(String.class);
      return parse(body);
    } catch (RuntimeException ignored) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "We could not suggest an address. Enter it manually or try again later.");
    }
  }

  static Suggestion parse(String body) {
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
  }

  private static String limit(String value, int length) {
    return value.length() > length ? value.substring(0, length) : value;
  }
}
