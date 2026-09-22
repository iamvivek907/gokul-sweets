import {
    apiClient
} from "@/services/apiClient";

import type {
    CreateOrderRequest,
    CustomerOrderResponse,
    CustomerOrderSummaryResponse,
    OrderResponse,
    UpdatePendingOrderRequest
} from "@/types/order";


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
            signal
        }
    );
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
            signal
        }
    );
}
