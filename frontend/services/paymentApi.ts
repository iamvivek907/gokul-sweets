import {apiClient} from "@/services/apiClient";
import type {
    CreatePaymentRequest,
    PaymentResponse,
    RazorpayVerificationRequest
} from "@/types/payment";

export function createPayment(
    request: CreatePaymentRequest,
    signal?: AbortSignal
): Promise<PaymentResponse> {
    return apiClient<PaymentResponse>("/api/payments", {
        method: "POST",
        body: JSON.stringify(request),
        signal
    });
}

export function refreshPayment(
    paymentId: number,
    signal?: AbortSignal
): Promise<PaymentResponse> {
    return apiClient<PaymentResponse>(
        `/api/payments/${paymentId}/refresh`,
        {method: "POST", signal}
    );
}

export function verifyRazorpayPayment(
    paymentId: number,
    request: RazorpayVerificationRequest,
    signal?: AbortSignal
): Promise<PaymentResponse> {
    return apiClient<PaymentResponse>(
        `/api/payments/${paymentId}/razorpay/verify`,
        {
            method: "POST",
            body: JSON.stringify(request),
            signal
        }
    );
}
