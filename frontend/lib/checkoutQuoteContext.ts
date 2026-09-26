import type {CustomerOrderResponse} from "@/types/order";

// The quote and the eventual reservation must reference the same live order.
export function pendingCheckoutAction(order: Pick<CustomerOrderResponse, "orderStatus" | "paymentStatus">,
                                      expiresAtMs: number, nowMs: number): "reuse" | "payment" | "paid" | "replace" {
    if (order.paymentStatus === "PAID") return "paid";
    if (order.paymentStatus === "PENDING") return "payment";
    return order.orderStatus === "PENDING_PAYMENT" && order.paymentStatus === null
        && Number.isFinite(expiresAtMs) && expiresAtMs > nowMs ? "reuse" : "replace";
}
