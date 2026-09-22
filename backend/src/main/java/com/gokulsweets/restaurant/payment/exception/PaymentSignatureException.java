package com.gokulsweets.restaurant.payment.exception;

import lombok.Getter;

@Getter
public class PaymentSignatureException extends RuntimeException {

    private final String code;

    public PaymentSignatureException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }
}
