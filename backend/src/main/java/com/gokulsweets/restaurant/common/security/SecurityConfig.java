package com.gokulsweets.restaurant.common.security;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffUserDetailsService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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

/** Backend security config contract and implementation. */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final StaffUserDetailsService staffUserDetailsService;

    private final WebCorsProperties webCorsProperties;

    private final Environment environment;

    /**
     * Passwords encoder.
     *
     * @return the password encoder result
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(SecurityConfig.class, "passwordEncoder()");
        try {
            return new BCryptPasswordEncoder();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, SecurityConfig.class, "passwordEncoder()");
        }
    }

    /**
     * Authentications provider.
     *
     * @param passwordEncoder the password encoder
     * @return the authentication provider result
     */
    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(SecurityConfig.class, "authenticationProvider(PasswordEncoder)");
        try {
            DaoAuthenticationProvider provider =
                    new DaoAuthenticationProvider(staffUserDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SecurityConfig.class,
                    "authenticationProvider(PasswordEncoder)");
        }
    }

    /**
     * Staffs session filter registration.
     *
     * @param filter the filter
     * @return the staff session filter registration result
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type =
                    org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
                            .Type.SERVLET)
    public org.springframework.boot.web.servlet.FilterRegistrationBean<
                    com.gokulsweets.restaurant.security.StaffSessionFilter>
            staffSessionFilterRegistration(
                    com.gokulsweets.restaurant.security.StaffSessionFilter filter) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SecurityConfig.class,
                        "staffSessionFilterRegistration(com.gokulsweets.restaurant.security.StaffSessionFilter)");
        try {
            var registration =
                    new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SecurityConfig.class,
                    "staffSessionFilterRegistration(com.gokulsweets.restaurant.security.StaffSessionFilter)");
        }
    }

    /**
     * Authentications manager.
     *
     * @param provider the provider
     * @return the authentication manager result
     */
    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            AuthenticationProvider provider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SecurityConfig.class, "authenticationManager(AuthenticationProvider)");
        try {
            return new org.springframework.security.authentication.ProviderManager(provider);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SecurityConfig.class,
                    "authenticationManager(AuthenticationProvider)");
        }
    }

    /**
     * Corses configuration source.
     *
     * @return the cors configuration source result
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(SecurityConfig.class, "corsConfigurationSource()");
        try {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(webCorsProperties.effectiveAllowedOrigins(environment));
            configuration.setAllowedMethods(
                    List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setExposedHeaders(List.of("Location", "X-Staff-CSRF"));
            configuration.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, SecurityConfig.class, "corsConfigurationSource()");
        }
    }

    /**
     * Security filter chain.
     *
     * @param http the http
     * @param authenticationProvider the authentication provider
     * @param staffSessionFilter the staff session filter
     * @return the security filter chain result
     * @throws Exception if the operation cannot complete
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type =
                    org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
                            .Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            com.gokulsweets.restaurant.security.StaffSessionFilter staffSessionFilter)
            throws Exception {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SecurityConfig.class,
                        "securityFilterChain(HttpSecurity,AuthenticationProvider,com.gokulsweets.restaurant.security.StaffSessionFilter)");
        try {
            http.cors(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable())
                    .addFilterBefore(
                            staffSessionFilter,
                            org.springframework.security.web.authentication.www
                                    .BasicAuthenticationFilter.class)
                    .authenticationProvider(authenticationProvider)
                    .authorizeHttpRequests( // Allow browser CORS preflight requests
                            auth ->
                                    auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                            .permitAll()
                                            .requestMatchers("/error")
                                            .permitAll()
                                            .requestMatchers("/api/print-agent/**")
                                            .permitAll()
                                            . // Credential verification and MFA enrollment precede
                                            // staff session creation.
                                            requestMatchers(
                                                    HttpMethod.POST,
                                                    "/api/admin/auth/login",
                                                    "/api/admin/auth/mfa/setup",
                                                    "/api/admin/auth/mfa/confirm",
                                                    "/api/admin/auth/owner-setup",
                                                    "/api/admin/auth/owner-recovery")
                                            .permitAll()
                                            .requestMatchers(
                                                    HttpMethod.GET, "/api/admin/auth/owner-setup")
                                            .permitAll()
                                            . // Admin APIs require authentication
                                            requestMatchers("/api/admin/**")
                                            .authenticated()
                                            .requestMatchers(
                                                    "/actuator/health",
                                                    "/api/storefront/**",
                                                    "/api/customer/identity/**",
                                                    "/api/occasion-enquiries",
                                                    "/api/occasion-enquiries/**",
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
                                                    "/api/reviews/**")
                                            .permitAll()
                                            .anyRequest()
                                            .authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SecurityConfig.class,
                    "securityFilterChain(HttpSecurity,AuthenticationProvider,com.gokulsweets.restaurant.security.StaffSessionFilter)");
        }
    }
}
