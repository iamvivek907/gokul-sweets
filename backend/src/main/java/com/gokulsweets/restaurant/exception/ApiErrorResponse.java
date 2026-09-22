package com.gokulsweets.restaurant.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    public ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
        this(
                timestamp,
                status,
                error,
                "REQUEST_FAILED",
                message,
                path,
                Map.of()
        );
    }

    public ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String code,
            String message,
            String path
    ) {
        this(timestamp, status, error, code, message, path, Map.of());
    }
}
