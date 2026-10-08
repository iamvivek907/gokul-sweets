package com.gokulsweets.restaurant.payment.exception;

import lombok.Getter;

/** Backend payment gateway exception contract and implementation. */
@Getter
public class PaymentGatewayException extends RuntimeException {

    private final String code;

    private final boolean retryable;

    /**
     * Creates a payment gateway exception instance.
     *
     * @param code the code
     * @param message the message
     * @param retryable the retryable
     */
    public PaymentGatewayException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    /**
     * Creates a payment gateway exception instance.
     *
     * @param code the code
     * @param message the message
     * @param retryable the retryable
     * @param cause the cause
     */
    public PaymentGatewayException(
            String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }
}
