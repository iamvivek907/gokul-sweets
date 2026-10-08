package com.gokulsweets.restaurant.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable razorpay payment verification request data contract.
 *
 * @param razorpayPaymentId the razorpay payment id
 * @param razorpayOrderId the razorpay order id
 * @param razorpaySignature the razorpay signature
 */
public record RazorpayPaymentVerificationRequest(
        @NotBlank(message = "Razorpay payment ID is required.") String razorpayPaymentId,
        @NotBlank(message = "Razorpay order ID is required.") String razorpayOrderId,
        @NotBlank(message = "Razorpay signature is required.") String razorpaySignature) {}
