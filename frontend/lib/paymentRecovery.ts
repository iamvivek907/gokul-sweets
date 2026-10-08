import type {PaymentResponse} from "@/types/payment";
import {parsePendingPayment} from "@/lib/pendingPaymentStorage";
import {getPaymentForOrder, refreshPayment} from "@/services/paymentApi";
import {ApiError} from "@/services/apiClient";
import {hasOpenedPaymentGateway} from "@/lib/paymentGatewayVisit";
import {isTemporaryPaymentFailure} from "@/lib/paymentPolling";

export function mergePaymentResponse(
    refreshed: PaymentResponse,
    existing:
        | PaymentResponse
        | ReturnType<typeof parsePendingPayment>
        | null
): PaymentResponse {

    // Checkout credentials belong to one attempt, never to its replacement.
    if (existing?.paymentId !== refreshed.paymentId || existing.provider !== refreshed.provider || existing.orderNumber !== refreshed.orderNumber) return refreshed;
    return {
        ...refreshed,

        providerPaymentId:
            refreshed.providerPaymentId
            ?? existing?.providerPaymentId
            ?? null,

        providerOrderId:
            refreshed.providerOrderId
            ?? existing?.providerOrderId
            ?? null,

        paymentSessionId:
            refreshed.paymentSessionId
            ?? existing?.paymentSessionId
            ?? null,

        paymentUrl:
            refreshed.paymentUrl
            ?? existing?.paymentUrl
            ?? null,

        checkoutKeyId:
            refreshed.checkoutKeyId
            ?? existing?.checkoutKeyId
            ?? null
    };
}


export async function refreshKnownPayment(known: PaymentResponse, resilient: boolean, {allowTemporaryFallback = true}: {allowTemporaryFallback?: boolean} = {}): Promise<PaymentResponse> {
    // History, another tab and restored pages can retain a stale PENDING snapshot.
    const lookup = await getPaymentForOrder(known.orderNumber);
    if (!lookup.payment || lookup.payment.orderNumber !== known.orderNumber) throw new Error("This payment could not be recovered. Please check My Orders before paying again.");
    const current = mergePaymentResponse(lookup.payment, known);
    if (current.paymentStatus !== "PENDING") return current;
    if (resilient && !hasOpenedPaymentGateway(current.orderNumber, current.paymentId)) return current;
    try {
        return mergePaymentResponse(await refreshPayment(current.paymentId), current);
    } catch (error) {
        // Read-only recovery may keep showing the known pending attempt during
        // an outage. A deliberate gateway launch must confirm its latest state.
        if (allowTemporaryFallback && resilient && error instanceof ApiError && isTemporaryPaymentFailure(error.status)) return current;
        throw error;
    }
}
