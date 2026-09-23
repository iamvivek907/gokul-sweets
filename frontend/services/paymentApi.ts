import {apiClient} from "@/services/apiClient";

import type {
    CreatePaymentRequest,
    PaymentProviderConfigurationResponse,
    PaymentResponse,
    RazorpayVerificationRequest
} from "@/types/payment";


/*
 * =========================================================
 * PAYMENT LOOKUP RESPONSE
 * =========================================================
 *
 * The backend always returns JSON:
 *
 * {
 *     "payment": null
 * }
 *
 * or:
 *
 * {
 *     "payment": {
 *         ...
 *     }
 * }
 *
 * This avoids attempting response.json() on an empty HTTP body.
 */

export interface PaymentLookupResponse {
    payment: PaymentResponse | null;
}


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
 * Used when returning from an external payment provider.
 *
 * The order number in the URL is the authoritative recovery
 * key. Browser localStorage is only a convenience.
 *
 * This is particularly important for PhonePe because the user
 * can leave the merchant site and return through a different
 * browser/app context.
 */

export function getPaymentForOrder(
    orderNumber: string,
    signal?: AbortSignal
): Promise<PaymentLookupResponse> {

    return apiClient<PaymentLookupResponse>(
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
