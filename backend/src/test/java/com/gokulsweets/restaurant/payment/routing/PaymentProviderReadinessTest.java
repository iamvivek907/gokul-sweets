package com.gokulsweets.restaurant.payment.routing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayProperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

class PaymentProviderReadinessTest {
    PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    PhonePeProperties phonepe = new PhonePeProperties();
    RazorpayProperties razorpay = new RazorpayProperties();
    MockEnvironment environment =
            new MockEnvironment()
                    .withProperty("gokul.environment-isolation.environment", "PROD")
                    .withProperty(
                            "gokul.environment-isolation.api-origin", "https://api.gokulsweets.in");
    PaymentProviderReadiness readiness =
            new PaymentProviderReadiness(registry, phonepe, razorpay, environment);

    @Test
    void requiresEnabledLiveRazorpayAndWebhookCredentialsWithoutLeakingKeys() {
        when(registry.enabledProviders()).thenReturn(Set.of(PaymentProviderType.RAZORPAY));
        razorpay.setKeyId("rzp_test_private");
        razorpay.setKeySecret("private-secret");
        var blocked = readiness.check(PaymentProviderType.RAZORPAY);
        assertThat(blocked.ready()).isFalse();
        assertThat(blocked.issues().toString())
                .doesNotContain("private-secret", "rzp_test_private");
        razorpay.setKeyId("rzp_live_private");
        razorpay.setWebhookSecret("webhook-private");
        assertThat(readiness.check(PaymentProviderType.RAZORPAY).ready()).isTrue();
        when(registry.enabledProviders()).thenReturn(Set.of(PaymentProviderType.PHONEPE));
        assertThat(readiness.check(PaymentProviderType.RAZORPAY).ready()).isFalse();
    }

    @Test
    void requiresBothProductionPhonepeEndpointsAndExactEnvironmentRoutes() {
        when(registry.enabledProviders()).thenReturn(Set.of(PaymentProviderType.PHONEPE));
        phonepe.setClientId("private-id");
        phonepe.setClientSecret("private-secret");
        phonepe.setClientVersion("1");
        phonepe.setWebhookChecksumKeyId("private-key");
        phonepe.setWebhookChecksumSecret("private-hook");
        phonepe.setRedirectUrl("https://gokulsweets.in/checkout");
        phonepe.setWebhookUrl("https://api.gokulsweets.in/api/payments/webhooks/phonepe");
        phonepe.setBaseUrl("https://api.phonepe.com/apis/pg");
        phonepe.setAuthorizationBaseUrl("https://api.phonepe.com/apis/identity-manager");
        assertThat(readiness.check(PaymentProviderType.PHONEPE).ready()).isTrue();
        phonepe.setAuthorizationBaseUrl("https://api-preprod.phonepe.com/apis/pg-sandbox");
        assertThat(readiness.check(PaymentProviderType.PHONEPE).ready()).isFalse();
        phonepe.setAuthorizationBaseUrl("https://api.phonepe.com/apis/identity-manager");
        phonepe.setRedirectUrl("https://dev.gokulsweets.in/checkout");
        assertThat(readiness.check(PaymentProviderType.PHONEPE).ready()).isFalse();
    }
}
