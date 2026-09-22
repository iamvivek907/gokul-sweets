package com.gokulsweets.restaurant.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record RazorpayPaymentVerificationRequest(
        @NotBlank(message = "Razorpay payment ID is required.")
        String razorpayPaymentId,

        @NotBlank(message = "Razorpay order ID is required.")
        String razorpayOrderId,

        @NotBlank(message = "Razorpay signature is required.")
        String razorpaySignature
) {
}
