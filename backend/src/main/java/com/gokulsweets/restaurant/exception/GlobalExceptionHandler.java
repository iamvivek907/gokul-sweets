package com.gokulsweets.restaurant.exception;

import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Backend global exception handler contract and implementation. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles upload size.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle upload size result
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException,HttpServletRequest)");
        try {
            String message =
                    request.getRequestURI().contains("homepage-campaigns")
                            ? "Campaign images must be under 5 MB and videos under 50 MB."
                            : "Media must be 5 MB or smaller.";
            return loggedResponse(
                    HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", message, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException,HttpServletRequest)");
        }
    }

    /**
     * Handles payment signature.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle payment signature result
     */
    @ExceptionHandler(PaymentSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentSignature(
            PaymentSignatureException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handlePaymentSignature(PaymentSignatureException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handlePaymentSignature(PaymentSignatureException,HttpServletRequest)");
        }
    }

    /**
     * Handles payment gateway.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle payment gateway result
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentGateway(
            PaymentGatewayException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handlePaymentGateway(PaymentGatewayException,HttpServletRequest)");
        try {
            HttpStatus status =
                    exception.isRetryable()
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.BAD_GATEWAY;
            return loggedResponse(
                    status,
                    exception.getCode(),
                    exception.getMessage(),
                    request,
                    Map.of("retryable", exception.isRetryable()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handlePaymentGateway(PaymentGatewayException,HttpServletRequest)");
        }
    }

    /**
     * Handles inventory not found.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle inventory not found result
     */
    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleInventoryNotFound(
            InventoryNotFoundException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleInventoryNotFound(InventoryNotFoundException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleInventoryNotFound(InventoryNotFoundException,HttpServletRequest)");
        }
    }

    /**
     * Handles inventory conflict.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle inventory conflict result
     */
    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleInventoryConflict(
            InventoryConflictException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleInventoryConflict(InventoryConflictException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.CONFLICT,
                    exception.getCode(),
                    exception.getMessage(),
                    request,
                    exception.getDetails());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleInventoryConflict(InventoryConflictException,HttpServletRequest)");
        }
    }

    /**
     * Handles concurrency failure.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle concurrency failure result
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrencyFailure(
            ConcurrencyFailureException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleConcurrencyFailure(ConcurrencyFailureException,HttpServletRequest)");
        try {
            log.warn(
                    "Concurrent request conflict: method={}, path={}",
                    request.getMethod(),
                    request.getRequestURI());
            return buildResponse(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_UPDATE",
                    "The data changed while this request was being processed. Please refresh and"
                            + " try again.",
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleConcurrencyFailure(ConcurrencyFailureException,HttpServletRequest)");
        }
    }

    /**
     * Handles illegal argument.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle illegal argument result
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleIllegalArgument(IllegalArgumentException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    messageOrDefault(exception.getMessage(), "Request is invalid."),
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleIllegalArgument(IllegalArgumentException,HttpServletRequest)");
        }
    }

    /**
     * Handles ineligible offer.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle ineligible offer result
     */
    @ExceptionHandler(com.gokulsweets.restaurant.order.service.OfferIneligibleException.class)
    public ResponseEntity<ApiErrorResponse> handleIneligibleOffer(
            com.gokulsweets.restaurant.order.service.OfferIneligibleException exception,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleIneligibleOffer(com.gokulsweets.restaurant.order.service.OfferIneligibleException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.CONFLICT, "OFFER_INELIGIBLE", exception.getMessage(), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleIneligibleOffer(com.gokulsweets.restaurant.order.service.OfferIneligibleException,HttpServletRequest)");
        }
    }

    /**
     * Handles illegal state.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle illegal state result
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleIllegalState(IllegalStateException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE",
                    messageOrDefault(
                            exception.getMessage(),
                            "Request cannot be completed in the current state."),
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleIllegalState(IllegalStateException,HttpServletRequest)");
        }
    }

    /**
     * Handles validation.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle validation result
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleValidation(MethodArgumentNotValidException,HttpServletRequest)");
        try {
            FieldError fieldError = exception.getBindingResult().getFieldError();
            String message =
                    fieldError == null
                            ? "Request validation failed."
                            : "Invalid value for "
                                    + fieldError.getField()
                                    + ": "
                                    + messageOrDefault(
                                            fieldError.getDefaultMessage(), "value is invalid");
            return loggedResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleValidation(MethodArgumentNotValidException,HttpServletRequest)");
        }
    }

    /**
     * Handles unreadable body.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle unreadable body result
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleUnreadableBody(HttpMessageNotReadableException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.BAD_REQUEST,
                    "UNREADABLE_REQUEST",
                    "Request contains an invalid or unsupported value.",
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleUnreadableBody(HttpMessageNotReadableException,HttpServletRequest)");
        }
    }

    /**
     * Handles access denied.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle access denied result
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleAccessDenied(AccessDeniedException,HttpServletRequest)");
        try {
            return loggedResponse(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "You do not have permission to perform this action.",
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleAccessDenied(AccessDeniedException,HttpServletRequest)");
        }
    }

    /**
     * Handles request status.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle request status result
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestStatus(
            org.springframework.web.server.ResponseStatusException exception,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleRequestStatus(org.springframework.web.server.ResponseStatusException,HttpServletRequest)");
        try {
            // Preserve intentional authentication/validation failures instead of turning them into
            // a 500.
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            return buildResponse(
                    status,
                    "REQUEST_REJECTED",
                    messageOrDefault(exception.getReason(), status.getReasonPhrase()),
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleRequestStatus(org.springframework.web.server.ResponseStatusException,HttpServletRequest)");
        }
    }

    /**
     * Handles unexpected.
     *
     * @param exception the exception
     * @param request the request
     * @return the handle unexpected result
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "handleUnexpected(Exception,HttpServletRequest)");
        try {
            log.error(
                    "Unexpected request failure: method={}, path={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception);
            return buildResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "Something went wrong. Please try again.",
                    request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "handleUnexpected(Exception,HttpServletRequest)");
        }
    }

    /**
     * Loggeds response.
     *
     * @param status the status
     * @param code the code
     * @param message the message
     * @param request the request
     * @return the logged response result
     */
    private ResponseEntity<ApiErrorResponse> loggedResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "loggedResponse(HttpStatus,String,String,HttpServletRequest)");
        try {
            return loggedResponse(status, code, message, request, Map.of());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "loggedResponse(HttpStatus,String,String,HttpServletRequest)");
        }
    }

    /**
     * Loggeds response.
     *
     * @param status the status
     * @param code the code
     * @param message the message
     * @param request the request
     * @param details the details
     * @return the logged response result
     */
    private ResponseEntity<ApiErrorResponse> loggedResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "loggedResponse(HttpStatus,String,String,HttpServletRequest,Map<String,Object>)");
        try {
            log.warn(
                    "Request rejected: method={}, path={}, status={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    code,
                    message);
            return buildResponse(status, code, message, request, details);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "loggedResponse(HttpStatus,String,String,HttpServletRequest,Map<String,Object>)");
        }
    }

    /**
     * Builds response.
     *
     * @param status the status
     * @param code the code
     * @param message the message
     * @param request the request
     * @return the build response result
     */
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "buildResponse(HttpStatus,String,String,HttpServletRequest)");
        try {
            return buildResponse(status, code, message, request, Map.of());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "buildResponse(HttpStatus,String,String,HttpServletRequest)");
        }
    }

    /**
     * Builds response.
     *
     * @param status the status
     * @param code the code
     * @param message the message
     * @param request the request
     * @param details the details
     * @return the build response result
     */
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        GlobalExceptionHandler.class,
                        "buildResponse(HttpStatus,String,String,HttpServletRequest,Map<String,Object>)");
        try {
            return ResponseEntity.status(status)
                    .body(
                            new ApiErrorResponse(
                                    Instant.now(),
                                    status.value(),
                                    status.getReasonPhrase(),
                                    code,
                                    message,
                                    request.getRequestURI(),
                                    details));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "buildResponse(HttpStatus,String,String,HttpServletRequest,Map<String,Object>)");
        }
    }

    /**
     * Messages or default.
     *
     * @param message the message
     * @param defaultMessage the default message
     * @return the message or default result
     */
    private String messageOrDefault(String message, String defaultMessage) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(GlobalExceptionHandler.class, "messageOrDefault(String,String)");
        try {
            return message == null || message.isBlank() ? defaultMessage : message;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    GlobalExceptionHandler.class,
                    "messageOrDefault(String,String)");
        }
    }
}
