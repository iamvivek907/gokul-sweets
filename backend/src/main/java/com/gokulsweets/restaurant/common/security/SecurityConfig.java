package com.gokulsweets.restaurant.common.security;

import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final StaffUserDetailsService staffUserDetailsService;
    private final WebCorsProperties webCorsProperties;
    private final Environment environment;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(staffUserDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.gokulsweets.restaurant.security.StaffSessionFilter>
            staffSessionFilterRegistration(com.gokulsweets.restaurant.security.StaffSessionFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            AuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                webCorsProperties.effectiveAllowedOrigins(environment)
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(
                List.of("Location", "X-Staff-CSRF")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            com.gokulsweets.restaurant.security.StaffSessionFilter staffSessionFilter
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .addFilterBefore(staffSessionFilter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)

                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(auth -> auth

                        // Allow browser CORS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .requestMatchers("/error")
                        .permitAll()

                        .requestMatchers("/api/print-agent/**")
                        .permitAll()

                        // Credential verification and MFA enrollment precede staff session creation.
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login",
                                "/api/admin/auth/mfa/setup", "/api/admin/auth/mfa/confirm")
                        .permitAll()

                        // Admin APIs require authentication
                        .requestMatchers("/api/admin/**")
                        .authenticated()

                        .requestMatchers(
                                "/actuator/health",
                                "/api/storefront/**",
                                "/api/customer/identity/**",

                                "/api/branches",
                                "/api/branches/**",

                                "/api/categories",
                                "/api/categories/**",

                                "/api/products",
                                "/api/products/**",

                                "/api/menu",
                                "/api/menu/**",

                                "/api/tax-categories",
                                "/api/tax-categories/**",

                                "/api/orders",
                                "/api/orders/**",

                                "/api/payments",
                                "/api/payments/**",

                                "/api/reviews",
                                "/api/reviews/**"
                        )
                        .permitAll()

                        .anyRequest()
                        .authenticated()
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
