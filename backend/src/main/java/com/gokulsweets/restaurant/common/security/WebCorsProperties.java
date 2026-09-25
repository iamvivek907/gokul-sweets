package com.gokulsweets.restaurant.common.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "gokul.web")
@Validated
@Getter
@Setter
public class WebCorsProperties {

    // The deployment environment supplies the exact customer origin. In the
    // absence of that setting, only local development is allowed to use the API.
    @NotEmpty
    private List<@NotBlank @Pattern(regexp = "https://[^/\\s*]+(?::[0-9]+)?|http://localhost(?::[0-9]+)?") String>
            allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    /** Optional DEV/PROD CORS rollout; a mistaken explicit allowlist fails startup. */
    public List<String> effectiveAllowedOrigins(Environment environment) {
        if (!environment.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)) {
            return List.copyOf(allowedOrigins);
        }

        String expected = switch (environment.getProperty(
                "gokul.environment-isolation.environment", "")) {
            case "DEV" -> "https://dev.gokulsweets.in";
            case "PROD" -> "https://gokulsweets.in";
            default -> throw new IllegalStateException(
                    "Environment CORS requires GOKUL_DEPLOYMENT_ENVIRONMENT=DEV or PROD.");
        };

        if (environment.getProperty("GOKUL_ALLOWED_ORIGINS") != null
                && !allowedOrigins.equals(List.of(expected))) {
            throw new IllegalStateException(
                    "GOKUL_ALLOWED_ORIGINS must match the declared deployment storefront exactly.");
        }
        return List.of(expected);
    }
}
