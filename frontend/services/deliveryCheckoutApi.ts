import {apiClient} from "@/services/apiClient";
import type {CreateOrderItemRequest} from "@/types/order";

export interface DeliveryCartCheck {
    branchId: number;
    locality: string;
    postalCode: string;
    serviceDate: string;
    latitude: number;
    longitude: number;
    items: CreateOrderItemRequest[];
}

export interface DeliveryOrderDraft {
    quote: DeliveryCartCheck;
    windowId: number;
    customerName: string;
    customerPhone: string;
    addressLine: string;
    acceptedQuoteToken?: string;
}

export interface DeliveryAcceptedQuote {
    windowId: number;
    serviceDate: string;
    startsAt: string;
    endsAt: string;
    items: {productName: string; unitPrice: string; taxRate: string; taxAmount: string; totalAmount: string}[];
    subtotal: string;
    taxAmount: string;
    priorityCharge: string;
    deliveryFee: string;
    paymentFee?: string;
    paymentFeeTax?: string;
    paymentFeeRate?: string;
    totalAmount: string;
    currency: string;
    expiresAt: string;
    token: string;
}

export interface CreatedDeliveryOrder {
    id: number;
    orderNumber: string;
    branchId: number;
    windowId: number;
    orderStatus: "PENDING_PAYMENT";
    totalAmount: number;
    reservationExpiresAt: string;
    createdAt: string;
}

export function previewDeliveryPrice(request: DeliveryOrderDraft): Promise<DeliveryAcceptedQuote> {
    return apiClient<DeliveryAcceptedQuote>("/api/storefront/delivery/accepted-quote", {
        method: "POST", body: JSON.stringify(request), credentials: "include"
    });
}

export function createDeliveryOrder(request: DeliveryOrderDraft, idempotencyKey: string): Promise<CreatedDeliveryOrder> {
    return apiClient<CreatedDeliveryOrder>("/api/storefront/delivery/orders", {
        method: "POST", headers: {"Idempotency-Key": idempotencyKey},
        body: JSON.stringify(request), credentials: "include"
    });
}
