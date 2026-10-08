package com.gokulsweets.restaurant.payment.exception;

import lombok.Getter;

/** Backend payment signature exception contract and implementation. */
@Getter
public class PaymentSignatureException extends RuntimeException {

    private final String code;

    public PaymentSignatureException(String code, String message) {
        super(message);
        this.code = code;
    }
}
