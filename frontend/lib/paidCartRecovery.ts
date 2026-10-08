import {clearPickupSlot} from "@/lib/checkoutStorage";
import {releaseCompletedMenuPickup} from "@/lib/menuPickupMode";
import {createCartFingerprint} from "@/lib/cartFingerprint";
import {clearStoredCart, getCartSnapshot, parseCart} from "@/lib/cartStorage";
import {clearPendingOrder, getPendingOrderSnapshot, parsePendingOrder} from "@/lib/pendingOrderStorage";
import {clearPendingPayment, getPendingPaymentSnapshot, parsePendingPayment} from "@/lib/pendingPaymentStorage";

interface CompletedPickupOwner {
    orderNumber: string;
    paymentStatus: string | null;
    branchId?: number;
    pickupSlotId?: number | null;
}

/** Only a server-confirmed PAID order may call this. Never erase a later, unrelated cart. */
export function reconcilePaidCart(orderNumber: string, completed?: CompletedPickupOwner): boolean {
    if (typeof window === "undefined") return false;
    const order = parsePendingOrder(getPendingOrderSnapshot());
    const payment = parsePendingPayment(getPendingPaymentSnapshot());
    const matchingOrder = order?.orderNumber === orderNumber ? order : null;
    const matchingPayment = payment?.orderNumber === orderNumber ? payment : null;
    if (!matchingOrder && !matchingPayment) return false;

    if (completed && (completed.orderNumber !== orderNumber || completed.paymentStatus !== "PAID")) return false;
    const owner = completed && Number.isSafeInteger(completed.branchId) && (Number.isSafeInteger(completed.pickupSlotId) || completed.pickupSlotId === null)
        ? {branchId:completed.branchId!,pickupSlotId:completed.pickupSlotId!} : matchingOrder;
    const cart = parseCart(getCartSnapshot());
    const fingerprint = matchingOrder?.cartFingerprint ?? matchingPayment!.cartFingerprint;
    const sameCart = cart.items.length > 0 && (!order || !!matchingOrder) &&
        (!owner || cart.branchId === owner.branchId) &&
        createCartFingerprint(cart.items) === fingerprint;

    // Payment-only recovery waits for the paid order details; keep its fingerprint
    // so the summary page can safely reconcile without guessing branch/slot identity.
    if (sameCart && !owner) return false;
    if (sameCart) {
        const released = owner && releaseCompletedMenuPickup(owner.branchId,owner.pickupSlotId,window.localStorage.getItem("gokul-selected-pickup-slot") ?? "");
        clearStoredCart();
        if (released) clearPickupSlot();
        window.localStorage.removeItem("gokul-customer-details");
    }
    if (matchingPayment) clearPendingPayment();
    if (matchingOrder) clearPendingOrder();
    return sameCart;
}
