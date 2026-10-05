import {
    apiClient
} from "@/services/apiClient";

import type {
    CreateOrderRequest,
    CheckoutQuote,
    CustomerOrderResponse,
    CustomerOrderSummaryResponse,
    OrderResponse,
    UpdatePendingOrderRequest
} from "@/types/order";

export async function previewCheckoutQuote(request: CreateOrderRequest, pendingOrderNumber?: string, signal: AbortSignal = AbortSignal.timeout(15000)): Promise<CheckoutQuote> {
    const path = pendingOrderNumber
        ? `/api/orders/${encodeURIComponent(pendingOrderNumber)}/quote`
        : "/api/orders/quote";
    return apiClient<CheckoutQuote>(path, {method: "POST", body: JSON.stringify(request), credentials: "include", signal});
}


export async function createOrder(
    request: CreateOrderRequest,
    idempotencyKey: string,
    signal?: AbortSignal
): Promise<OrderResponse> {

    return apiClient<OrderResponse>(
        "/api/orders",
        {
            method: "POST",
            headers: {
                "Idempotency-Key":
                    idempotencyKey
            },
            body: JSON.stringify(request),
            credentials: "include",
            signal
        }
    );
}

/** Only the currently verified subject's orders; excludes phone-matched guest history. */
export function getVerifiedCustomerOrders(signal?: AbortSignal): Promise<CustomerOrderSummaryResponse[]> {
    return apiClient<CustomerOrderSummaryResponse[]>("/api/customer/identity/orders", {
        credentials: "include", signal
    });
}


export async function updatePendingCheckout(
    orderNumber: string,
    request: UpdatePendingOrderRequest,
    signal?: AbortSignal
): Promise<OrderResponse> {

    return apiClient<OrderResponse>(
        `/api/orders/${encodeURIComponent(orderNumber)}/checkout`,
        {
            method: "PUT",
            body: JSON.stringify(request),
            credentials: "include",
            signal
        }
    );
}


export async function getCustomerOrder(
    orderNumber: string,
    signal?: AbortSignal
): Promise<CustomerOrderResponse> {

    return apiClient<CustomerOrderResponse>(
        `/api/orders/${encodeURIComponent(orderNumber)}`,
        {
            method: "GET",
            credentials: "include",
            signal
        }
    );
}


export async function getCustomerOrderHistory(
    orderNumbers: string[],
    signal?: AbortSignal
): Promise<CustomerOrderSummaryResponse[]> {

    return apiClient<CustomerOrderSummaryResponse[]>(
        "/api/orders/history",
        {
            method: "POST",
            body: JSON.stringify({
                orderNumbers
            }),
            credentials: "include",
            signal
        }
    );
}

export interface VerifiedOrderPage {orders:CustomerOrderSummaryResponse[];nextBefore:string|null}
export function getVerifiedOrderPage(before:string|null=null,signal?:AbortSignal):Promise<VerifiedOrderPage>{
    const query=new URLSearchParams({limit:"10"});if(before)query.set("before",before);
    return apiClient<VerifiedOrderPage>(`/api/customer/identity/orders/page?${query}`,{credentials:"include",signal});
}
