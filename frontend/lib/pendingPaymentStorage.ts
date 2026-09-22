import type {
    PaymentProviderType,
    PaymentStatus,
    PendingPaymentSession
} from "@/types/payment";

const STORAGE_KEY = "gokul-pending-payment";
const CHANGE_EVENT = "gokul-pending-payment-change";
const PROVIDERS: PaymentProviderType[] = ["RAZORPAY", "PAYTM"];
const STATUSES: PaymentStatus[] = [
    "PENDING",
    "PAID",
    "FAILED",
    "EXPIRED",
    "REFUND_PENDING",
    "REFUNDED",
    "REFUND_FAILED"
];

export function getPendingPaymentSnapshot(): string {
    if (typeof window === "undefined") return "";
    return window.localStorage.getItem(STORAGE_KEY) ?? "";
}

export function getServerPendingPaymentSnapshot(): string {
    return "";
}

export function subscribeToPendingPayment(
    callback: () => void
): () => void {
    if (typeof window === "undefined") return () => {};

    const onStorage = (event: StorageEvent) => {
        if (event.key === STORAGE_KEY) callback();
    };
    window.addEventListener("storage", onStorage);
    window.addEventListener(CHANGE_EVENT, callback);

    return () => {
        window.removeEventListener("storage", onStorage);
        window.removeEventListener(CHANGE_EVENT, callback);
    };
}

export function parsePendingPayment(
    value: string
): PendingPaymentSession | null {
    if (!value) return null;

    try {
        const parsed = JSON.parse(value) as Partial<PendingPaymentSession>;
        if (
            typeof parsed.paymentId !== "number"
            || typeof parsed.orderNumber !== "string"
            || !PROVIDERS.includes(parsed.provider as PaymentProviderType)
            || !STATUSES.includes(parsed.paymentStatus as PaymentStatus)
            || typeof parsed.amount !== "number"
            || typeof parsed.currency !== "string"
            || typeof parsed.expiresAt !== "string"
            || typeof parsed.cartFingerprint !== "string"
        ) {
            return null;
        }

        return {
            paymentId: parsed.paymentId,
            orderNumber: parsed.orderNumber,
            provider: parsed.provider as PaymentProviderType,
            paymentStatus: parsed.paymentStatus as PaymentStatus,
            amount: parsed.amount,
            currency: parsed.currency,
            providerPaymentId: parsed.providerPaymentId ?? null,
            providerOrderId: parsed.providerOrderId ?? null,
            paymentSessionId: parsed.paymentSessionId ?? null,
            paymentUrl: parsed.paymentUrl ?? null,
            checkoutKeyId: parsed.checkoutKeyId ?? null,
            expiresAt: parsed.expiresAt,
            cartFingerprint: parsed.cartFingerprint
        };
    } catch {
        return null;
    }
}

export function savePendingPayment(
    payment: PendingPaymentSession
): void {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payment));
    window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function clearPendingPayment(): void {
    if (typeof window === "undefined") return;
    window.localStorage.removeItem(STORAGE_KEY);
    window.dispatchEvent(new Event(CHANGE_EVENT));
}
