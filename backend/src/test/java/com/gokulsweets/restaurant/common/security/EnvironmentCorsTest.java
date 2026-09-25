package com.gokulsweets.restaurant.common.security;

import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EnvironmentCorsTest {
    private CorsConfiguration policy(MockEnvironment environment, WebCorsProperties properties) {
        return new SecurityConfig(mock(StaffUserDetailsService.class), properties, environment)
                .corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/storefront/features"));
    }

    @Test
    void declaredDevAllowsOnlyItsStorefrontOnPreflight() {
        var environment = new MockEnvironment()
                .withProperty("gokul.web.environment-cors-enabled", "true")
                .withProperty("gokul.environment-isolation.environment", "DEV");
        var cors = policy(environment, new WebCorsProperties());

        assertThat(cors.checkOrigin("https://dev.gokulsweets.in"))
                .isEqualTo("https://dev.gokulsweets.in");
        assertThat(cors.checkOrigin("https://gokulsweets.in")).isNull();
        assertThat(cors.checkOrigin("https://api-dev.gokulsweets.in")).isNull();
        assertThat(cors.checkMethod(org.springframework.http.HttpMethod.GET)).isNotNull();
    }

    @Test
    void flagOffPreservesCurrentConfigurationAndProdIsExact() {
        var original = policy(new MockEnvironment(), new WebCorsProperties());
        assertThat(original.checkOrigin("http://localhost:3000"))
                .isEqualTo("http://localhost:3000");
        assertThat(original.checkOrigin("https://dev.gokulsweets.in")).isNull();

        var prod = new MockEnvironment()
                .withProperty("gokul.web.environment-cors-enabled", "true")
                .withProperty("gokul.environment-isolation.environment", "PROD");
        assertThat(policy(prod, new WebCorsProperties()).checkOrigin("https://gokulsweets.in"))
                .isEqualTo("https://gokulsweets.in");
        assertThat(policy(prod, new WebCorsProperties()).checkOrigin("https://dev.gokulsweets.in"))
                .isNull();
    }

    @Test
    void missingDeploymentOrConflictingExplicitOriginsFailsClosed() {
        var missing = new MockEnvironment().withProperty("gokul.web.environment-cors-enabled", "true");
        assertThatThrownBy(() -> policy(missing, new WebCorsProperties()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GOKUL_DEPLOYMENT_ENVIRONMENT");

        var conflicting = new MockEnvironment()
                .withProperty("gokul.web.environment-cors-enabled", "true")
                .withProperty("gokul.environment-isolation.environment", "DEV")
                .withProperty("GOKUL_ALLOWED_ORIGINS", "https://gokulsweets.in");
        var props = new WebCorsProperties();
        props.setAllowedOrigins(List.of("https://gokulsweets.in"));
        assertThatThrownBy(() -> policy(conflicting, props))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GOKUL_ALLOWED_ORIGINS");
    }
}
