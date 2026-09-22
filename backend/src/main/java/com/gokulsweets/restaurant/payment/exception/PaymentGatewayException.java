package com.gokulsweets.restaurant.payment.exception;

import lombok.Getter;

@Getter
public class PaymentGatewayException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public PaymentGatewayException(
            String code,
            String message,
            boolean retryable
    ) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public PaymentGatewayException(
            String code,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }
}
