package com.gokulsweets.restaurant.printing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PrintAgentAuthenticationService {

    @Value("${gokul.print-agent.api-key:}")
    private String configuredApiKey;


    public void authenticate(
            String providedApiKey
    ) {

        if (
                configuredApiKey == null
                        ||
                        configuredApiKey.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Print agent authentication is not configured."
            );
        }


        if (
                providedApiKey == null
                        ||
                        providedApiKey.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Print agent API key is required."
            );
        }


        boolean matches =
                MessageDigest.isEqual(
                        configuredApiKey
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        providedApiKey
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );


        if (!matches) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid print agent API key."
            );
        }
    }
}