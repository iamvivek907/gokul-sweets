package com.gokulsweets.restaurant.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable api error response data contract.
 *
 * @param timestamp the timestamp
 * @param status the status
 * @param error the error
 * @param code the code
 * @param message the message
 * @param path the path
 * @param details the details
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, Object> details) {

    /**
     * Creates a api error response instance.
     *
     * @param timestamp the timestamp
     * @param status the status
     * @param error the error
     * @param message the message
     * @param path the path
     */
    public ApiErrorResponse(
            Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, "REQUEST_FAILED", message, path, Map.of());
    }

    /**
     * Creates a api error response instance.
     *
     * @param timestamp the timestamp
     * @param status the status
     * @param error the error
     * @param code the code
     * @param message the message
     * @param path the path
     */
    public ApiErrorResponse(
            Instant timestamp, int status, String error, String code, String message, String path) {
        this(timestamp, status, error, code, message, path, Map.of());
    }
}
