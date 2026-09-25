package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Validates deployment wiring at startup when the rollout switch is enabled. */
@Component
@RequiredArgsConstructor
public class EnvironmentIsolationGuard {
    private final Environment environment;
    private final WebCorsProperties cors;
    private final PhonePeProperties phonePe;

    @PostConstruct
    void check() {
        if (!environment.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)) {
            return;
        }
        validate(new Settings(
                environment.getProperty("gokul.environment-isolation.environment", ""),
                environment.getProperty("gokul.environment-isolation.api-origin", ""),
                cors.getAllowedOrigins(), phonePe.getRedirectUrl(), phonePe.getWebhookUrl(),
                environment.getProperty("spring.datasource.url", ""),
                environment.getProperty("cloudflare.r2.bucket-name", ""),
                environment.getProperty("cloudflare.r2.public-url", ""),
                phonePe.getBaseUrl(), phonePe.getClientId(), phonePe.getWebhookChecksumKeyId()));
    }

    /** New payments get distinct provider identifiers; stored legacy IDs still reconcile unchanged. */
    public String phonePeMerchantOrderId(Long paymentId) {
        String prefix = environment.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                ? "GKS-" + environment.getProperty("gokul.environment-isolation.environment") + "-PPE-"
                : "GKS-PPE-";
        return prefix + paymentId;
    }

    public record Settings(String deployment, String apiOrigin, List<String> allowedOrigins,
                           String redirectBase, String webhookUrl, String databaseUrl,
                           String bucket, String imageOrigin, String phonePeBase,
                           String phonePeClientId, String webhookKeyId) { }

    static void validate(Settings s) {
        String storefront = switch (s.deployment()) {
            case "DEV" -> "https://dev.gokulsweets.in";
            case "PROD" -> "https://gokulsweets.in";
            default -> throw invalid("deployment must be DEV or PROD");
        };
        if (s.allowedOrigins() == null || !s.allowedOrigins().equals(List.of(storefront))) {
            throw invalid("CORS must allow exactly the declared storefront origin");
        }
        String api = origin(s.apiOrigin(), "backend API origin", true);
        String redirect = s.redirectBase() == null ? "" : s.redirectBase().replaceAll("/+$", "");
        if (!redirect.equals(storefront + "/checkout")) {
            throw invalid("PhonePe return base must be the declared storefront /checkout");
        }
        if (!java.util.Objects.equals(s.webhookUrl(), api + "/api/payments/webhooks/phonepe")) {
            throw invalid("PhonePe webhook must target the declared backend API");
        }
        if (s.databaseUrl() == null || !s.databaseUrl().startsWith("jdbc:postgresql://")) {
            throw invalid("a PostgreSQL database URL is required");
        }
        if (s.bucket() == null || s.bucket().isBlank() || s.bucket().equals("gokul-sweets-images")) {
            throw invalid("an explicitly separated R2 bucket is required");
        }
        origin(s.imageOrigin(), "R2 public URL", false);
        if (s.phonePeClientId() == null || s.phonePeClientId().isBlank()
                || s.webhookKeyId() == null || s.webhookKeyId().isBlank()) {
            throw invalid("environment-specific PhonePe client and webhook key IDs are required");
        }
        if ("PROD".equals(s.deployment()) && (s.phonePeBase() == null
                || s.phonePeBase().contains("preprod"))) {
            throw invalid("production cannot use the PhonePe sandbox");
        }
    }

    private static String origin(String raw, String name, boolean requireOrigin) {
        try {
            if (raw == null) {
                throw invalid(name + " must be a valid HTTPS URL");
            }
            URI uri = URI.create(raw);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (requireOrigin && uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw invalid(name + " must be an HTTPS origin");
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException e) {
            throw invalid(name + " must be a valid HTTPS URL");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Environment isolation: " + reason);
    }
}
