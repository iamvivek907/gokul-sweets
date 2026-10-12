/** A marker is written only when checkout is opened for this exact payment attempt. */
const KEY_PREFIX = "gokul-payment-gateway-opened:";

export function hasOpenedPaymentGateway(orderNumber: string, paymentId: number): boolean {
    if (typeof window === "undefined") return false;
    try {
        return window.localStorage.getItem(KEY_PREFIX + orderNumber) === String(paymentId);
    } catch {
        return false;
    }
}

export function markPaymentGatewayOpened(orderNumber: string, paymentId: number): void {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.setItem(KEY_PREFIX + orderNumber, String(paymentId));
    } catch {
        // Private browsing can reject storage; this page still supports manual checks.
    }
}

export function clearPaymentGatewayVisit(orderNumber: string): void {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.removeItem(KEY_PREFIX + orderNumber);
    } catch {
        // Storage is optional.
    }
}
