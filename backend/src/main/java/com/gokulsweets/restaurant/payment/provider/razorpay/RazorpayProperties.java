package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Backend razorpay properties contract and implementation. */
@Component
@ConfigurationProperties(prefix = "razorpay")
@Validated
@Getter
@Setter
public class RazorpayProperties {

    private String keyId = "";

    private String keySecret = "";

    private String webhookSecret = "";

    @NotBlank private String baseUrl = "https://api.razorpay.com";

    @NotBlank private String merchantName = "Gokul Sweets";

    @NotBlank private String themeColor = "#7a1625";

    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 10;

    @Min(1)
    @Max(120)
    private int requestTimeoutSeconds = 20;

    /** Requires api configuration. */
    public void requireApiConfiguration() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayProperties.class, "requireApiConfiguration()");
        try {
            if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
                throw new IllegalStateException("Razorpay API credentials are not configured.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayProperties.class,
                    "requireApiConfiguration()");
        }
    }

    /** Requires webhook configuration. */
    public void requireWebhookConfiguration() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayProperties.class, "requireWebhookConfiguration()");
        try {
            if (webhookSecret == null || webhookSecret.isBlank()) {
                throw new IllegalStateException("Razorpay webhook secret is not configured.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayProperties.class,
                    "requireWebhookConfiguration()");
        }
    }
}
