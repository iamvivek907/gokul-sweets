import {createCartFingerprint} from "@/lib/cartFingerprint";
import {clearStoredCart, getCartSnapshot, parseCart} from "@/lib/cartStorage";
import {clearPendingOrder, getPendingOrderSnapshot, parsePendingOrder} from "@/lib/pendingOrderStorage";
import {clearPendingPayment, getPendingPaymentSnapshot, parsePendingPayment} from "@/lib/pendingPaymentStorage";

/** Only a server-confirmed PAID order may call this. Never erase a later, unrelated cart. */
export function reconcilePaidCart(orderNumber: string): boolean {
    if (typeof window === "undefined") return false;
    const order = parsePendingOrder(getPendingOrderSnapshot());
    const payment = parsePendingPayment(getPendingPaymentSnapshot());
    const matchingOrder = order?.orderNumber === orderNumber ? order : null;
    const matchingPayment = payment?.orderNumber === orderNumber ? payment : null;
    if (!matchingOrder && !matchingPayment) return false;

    const cart = parseCart(getCartSnapshot());
    const fingerprint = matchingOrder?.cartFingerprint ?? matchingPayment!.cartFingerprint;
    const sameCart = cart.items.length > 0 &&
        (!matchingOrder || cart.branchId === matchingOrder.branchId) &&
        createCartFingerprint(cart.items) === fingerprint;

    if (sameCart) {
        clearStoredCart();
        window.localStorage.removeItem("gokul-selected-pickup-slot");
        window.localStorage.removeItem("gokul-customer-details");
    }
    if (matchingPayment) clearPendingPayment();
    if (matchingOrder) clearPendingOrder();
    return sameCart;
}
