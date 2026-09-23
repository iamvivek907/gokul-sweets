import {apiClient} from "@/services/apiClient";

import type {
    CreatePaymentRequest,
    PaymentProviderConfigurationResponse,
    PaymentResponse,
    RazorpayVerificationRequest
} from "@/types/payment";


/*
 * =========================================================
 * CREATE PAYMENT
 * =========================================================
 */

export function createPayment(
    request: CreatePaymentRequest,
    signal?: AbortSignal
): Promise<PaymentResponse> {

    return apiClient<PaymentResponse>(
        "/api/payments",
        {
            method: "POST",
            body: JSON.stringify(request),
            signal
        }
    );
}


/*
 * =========================================================
 * PAYMENT PROVIDER CONFIGURATION
 * =========================================================
 */

export function getPaymentProviderConfiguration(
    signal?: AbortSignal
): Promise<PaymentProviderConfigurationResponse> {

    return apiClient<PaymentProviderConfigurationResponse>(
        "/api/payments/providers",
        {
            method: "GET",
            signal
        }
    );
}


/*
 * =========================================================
 * GET PAYMENT FOR ORDER
 * =========================================================
 *
 * This is important after PhonePe / Paytm / other external
 * checkout redirects.
 *
 * We use the order number from the URL to recover the payment
 * from the backend when browser localStorage does not contain
 * the payment session anymore.
 */

export function getPaymentForOrder(
    orderNumber: string,
    signal?: AbortSignal
): Promise<PaymentResponse> {

    return apiClient<PaymentResponse>(
        `/api/payments/order/${encodeURIComponent(orderNumber)}`,
        {
            method: "GET",
            signal
        }
    );
}


/*
 * =========================================================
 * REFRESH PAYMENT
 * =========================================================
 */

export function refreshPayment(
    paymentId: number,
    signal?: AbortSignal
): Promise<PaymentResponse> {

    return apiClient<PaymentResponse>(
        `/api/payments/${paymentId}/refresh`,
        {
            method: "POST",
            signal
        }
    );
}


/*
 * =========================================================
 * RAZORPAY VERIFICATION
 * =========================================================
 */

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
