package com.gokulsweets.restaurant.payment.routing;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Checks configuration only; does not claim gateway reachability or merchant approval. */
@Service
@RequiredArgsConstructor
public class PaymentProviderReadiness {
    private final PaymentProviderRegistry registry;
    private final PhonePeProperties phonePe;
    private final RazorpayProperties razorpay;
    private final Environment environment;

    /**
     * Secret-free provider readiness.
     *
     * @param provider the provider
     * @param ready whether the configuration can accept new attempts
     * @param issues actionable configuration issues
     */
    public record Status(PaymentProviderType provider, boolean ready, List<String> issues) {}

    /**
     * Checks a supported provider's configuration without publishing credential values.
     *
     * @param provider the provider
     * @return readiness and redacted issues
     */
    public Status check(PaymentProviderType provider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderReadiness.class, "check(PaymentProviderType)");
        try {

            var issues = new ArrayList<String>();
            if (!registry.enabledProviders().contains(provider))
                issues.add("Enable this provider in PAYMENT_ENABLED_PROVIDERS on the backend.");
            boolean prod =
                    "PROD"
                            .equals(
                                    environment.getProperty(
                                            "gokul.environment-isolation.environment"));
            String api = environment.getProperty("gokul.environment-isolation.api-origin", "");
            String storefront = prod ? "https://gokulsweets.in" : "https://dev.gokulsweets.in";
            if (provider == PaymentProviderType.PHONEPE) {
                if (blank(phonePe.getClientId())
                        || blank(phonePe.getClientSecret())
                        || blank(phonePe.getClientVersion()))
                    issues.add("Configure PhonePe API credentials and client version.");
                if (blank(phonePe.getWebhookChecksumKeyId())
                        || blank(phonePe.getWebhookChecksumSecret()))
                    issues.add("Configure PhonePe webhook credentials.");
                if (!java.util.Objects.equals(phonePe.getRedirectUrl(), storefront + "/checkout"))
                    issues.add("Set PhonePe redirect to this environment's storefront /checkout.");
                if (!java.util.Objects.equals(
                        phonePe.getWebhookUrl(), api + "/api/payments/webhooks/phonepe"))
                    issues.add(
                            "Set PhonePe webhook to this backend's"
                                    + " /api/payments/webhooks/phonepe.");
                if (prod
                        && (!"https://api.phonepe.com/apis/pg".equals(phonePe.getBaseUrl())
                                || !"https://api.phonepe.com/apis/identity-manager"
                                        .equals(phonePe.getAuthorizationBaseUrl())))
                    issues.add("Configure live PhonePe payment and authorization endpoints.");
                if (!prod
                        && (blank(phonePe.getBaseUrl())
                                || blank(phonePe.getAuthorizationBaseUrl())))
                    issues.add("Configure PhonePe payment and authorization endpoints.");
            } else if (provider == PaymentProviderType.RAZORPAY) {
                if (blank(razorpay.getKeyId()) || blank(razorpay.getKeySecret()))
                    issues.add("Configure Razorpay API credentials.");
                if (blank(razorpay.getWebhookSecret()))
                    issues.add("Configure Razorpay webhook secret.");
                if (prod
                        && (blank(razorpay.getKeyId())
                                || !razorpay.getKeyId().startsWith("rzp_live_")))
                    issues.add("Production requires a live Razorpay key.");
                if (!"https://api.razorpay.com".equals(razorpay.getBaseUrl()))
                    issues.add("Use the official Razorpay API endpoint.");
            } else issues.add("This provider has not been approved for runtime switching.");
            return new Status(provider, issues.isEmpty(), List.copyOf(issues));

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentProviderReadiness.class,
                    "check(PaymentProviderType)");
        }
    }

    /**
     * Tests an optional credential for presence.
     *
     * @param value the value
     * @return whether it is blank
     */
    private boolean blank(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderReadiness.class, "blank(String)");
        try {
            return value == null || value.isBlank();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentProviderReadiness.class, "blank(String)");
        }
    }
}
