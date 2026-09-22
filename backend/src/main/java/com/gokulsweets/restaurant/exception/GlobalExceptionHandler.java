package com.gokulsweets.restaurant.exception;

import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import com.gokulsweets.restaurant.payment.exception.PaymentSignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception, HttpServletRequest request
    ) {
        return loggedResponse(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE",
                "Media must be 5 MB or smaller.", request);
    }

    @ExceptionHandler(PaymentSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentSignature(
            PaymentSignatureException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.BAD_REQUEST,
                exception.getCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentGateway(
            PaymentGatewayException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = exception.isRetryable()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;

        return loggedResponse(
                status,
                exception.getCode(),
                exception.getMessage(),
                request,
                Map.of("retryable", exception.isRetryable())
        );
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleInventoryNotFound(
            InventoryNotFoundException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.NOT_FOUND,
                exception.getCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleInventoryConflict(
            InventoryConflictException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getMessage(),
                request,
                exception.getDetails()
        );
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrencyFailure(
            ConcurrencyFailureException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Concurrent request conflict: method={}, path={}",
                request.getMethod(),
                request.getRequestURI()
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_UPDATE",
                "The data changed while this request was being processed. Please refresh and try again.",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                messageOrDefault(exception.getMessage(), "Request is invalid."),
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.CONFLICT,
                "INVALID_STATE",
                messageOrDefault(
                        exception.getMessage(),
                        "Request cannot be completed in the current state."
                ),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "Request validation failed."
                : "Invalid value for "
                + fieldError.getField()
                + ": "
                + messageOrDefault(
                        fieldError.getDefaultMessage(),
                        "value is invalid"
                );

        return loggedResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.BAD_REQUEST,
                "UNREADABLE_REQUEST",
                "Request contains an invalid or unsupported value.",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return loggedResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have permission to perform this action.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected request failure: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Something went wrong. Please try again.",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> loggedResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return loggedResponse(status, code, message, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> loggedResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        log.warn(
                "Request rejected: method={}, path={}, status={}, code={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                code,
                message
        );

        return buildResponse(status, code, message, request, details);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return buildResponse(status, code, message, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message,
                        request.getRequestURI(),
                        details
                )
        );
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank()
                ? defaultMessage
                : message;
    }
}
