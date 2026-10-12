export type PaymentProviderType = "RAZORPAY" | "PAYTM" | "PHONEPE";

export type PaymentStatus =
    | "PENDING"
    | "PAID"
    | "FAILED"
    | "EXPIRED"
    | "REFUND_PENDING"
    | "REFUNDED"
    | "REFUND_FAILED";

export interface CreatePaymentRequest {
    orderNumber: string;
    provider?: PaymentProviderType;
}

export interface PaymentProviderConfigurationResponse {
    defaultProvider: PaymentProviderType;
    enabledProviders: PaymentProviderType[];
}

export interface RazorpayVerificationRequest {
    razorpayPaymentId: string;
    razorpayOrderId: string;
    razorpaySignature: string;
}

export interface PaymentResponse {
    paymentId: number;
    orderNumber: string;
    provider: PaymentProviderType;
    paymentStatus: PaymentStatus;
    amount: number;
    currency: string;
    providerPaymentId: string | null;
    providerOrderId: string | null;
    paymentSessionId: string | null;
    paymentUrl: string | null;
    checkoutKeyId: string | null;
    expiresAt: string;
}

export interface PendingPaymentSession extends PaymentResponse {
    cartFingerprint: string;
}
