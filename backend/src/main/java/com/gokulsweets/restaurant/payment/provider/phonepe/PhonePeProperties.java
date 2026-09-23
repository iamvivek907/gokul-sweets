package com.gokulsweets.restaurant.payment.provider.phonepe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "phonepe")
@Validated
@Getter
@Setter
public class PhonePeProperties {

    @NotBlank
    private String clientId = "";

    @NotBlank
    private String clientSecret = "";

    @NotBlank
    private String clientVersion = "";

    @NotBlank
    private String baseUrl = "";

    @NotBlank
    private String authorizationBaseUrl = "";

    @NotBlank
    private String authorizationPath = "/v1/oauth/token";

    @NotBlank
    private String createPaymentPath = "/checkout/v2/pay";

    @NotBlank
    private String orderStatusPathTemplate =
            "/checkout/v2/order/{merchantOrderId}/status";

    @NotBlank
    private String redirectUrl = "";

    private String webhookUrl = "";

    private String webhookChecksumKeyId = "";

    private String webhookChecksumSecret = "";

    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 10;

    @Min(1)
    @Max(120)
    private int requestTimeoutSeconds = 20;

    @Min(5)
    @Max(300)
    private int tokenExpirySafetySeconds = 30;

    public void requireApiConfiguration() {
        if (clientId.isBlank()
                || clientSecret.isBlank()
                || clientVersion.isBlank()) {
            throw new IllegalStateException(
                    "PhonePe API credentials are not configured."
            );
        }
    }

    public void requireRedirectConfiguration() {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalStateException(
                    "PhonePe redirect URL is not configured."
            );
        }
    }

    public void requireWebhookConfiguration() {
        if (webhookChecksumKeyId == null
                || webhookChecksumKeyId.isBlank()
                || webhookChecksumSecret == null
                || webhookChecksumSecret.isBlank()) {
            throw new IllegalStateException(
                    "PhonePe webhook HMAC credentials are not configured."
            );
        }
    }
}