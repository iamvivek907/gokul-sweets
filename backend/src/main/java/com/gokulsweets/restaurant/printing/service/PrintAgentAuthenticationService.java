package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Coordinates print agent authentication operations. */
@Service
public class PrintAgentAuthenticationService {

    @Value("${gokul.print-agent.api-key:}")
    private String configuredApiKey;

    /**
     * Authenticates the operation.
     *
     * @param providedApiKey the provided api key
     */
    public void authenticate(String providedApiKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PrintAgentAuthenticationService.class, "authenticate(String)");
        try {
            if (configuredApiKey == null || configuredApiKey.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Print agent authentication is not configured.");
            }
            if (providedApiKey == null || providedApiKey.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Print agent API key is required.");
            }
            boolean matches =
                    MessageDigest.isEqual(
                            configuredApiKey.getBytes(StandardCharsets.UTF_8),
                            providedApiKey.getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid print agent API key.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PrintAgentAuthenticationService.class,
                    "authenticate(String)");
        }
    }
}
