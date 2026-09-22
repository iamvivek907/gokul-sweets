import type {
    PickupType
} from "@/types/pickup";

import type {
    PaymentStatus
} from "@/types/payment";

import type {
    ProductSaleMode
} from "@/types/menu";


export type OrderStatus =
    | "PENDING_PAYMENT"
    | "CONFIRMED"
    | "PREPARING"
    | "READY_FOR_PICKUP"
    | "PICKED_UP"
    | "PAYMENT_FAILED"
    | "CANCELLED"
    | "NO_SHOW"
    | "PICKUP_WINDOW_EXPIRED";


export interface CreateOrderItemRequest {
    productId: number;
    quantity: number | null;
    weightGrams: number | null;
}


export interface CreateOrderRequest {
    branchId: number;
    pickupSlotId: number;
    customerName: string;
    customerPhone: string;
    pickupType: PickupType;
    items: CreateOrderItemRequest[];
}


export interface UpdatePendingOrderRequest {
    pickupSlotId: number;
    pickupType: PickupType;
    items: CreateOrderItemRequest[];
}


export interface OrderItemResponse {
    id: number;
    productId: number;
    productName: string;
    saleMode: ProductSaleMode;
    quantity: number;
    weightGrams: number | null;
    unitPrice: number;
    taxRate: number;
    taxAmount: number;
    lineTotal: number;
}


export interface OrderResponse {
    id: number;
    orderNumber: string;
    branchId: number;
    pickupSlotId: number;
    pickupDate: string;
    pickupStartTime: string;
    pickupEndTime: string;
    customerName: string;
    customerPhone: string;
    pickupType: PickupType;
    priorityCharge: number;
    subtotal: number;
    taxAmount: number;
    totalAmount: number;
    orderStatus: OrderStatus;
    adminOverride: boolean;
    overrideReason: string | null;
    items: OrderItemResponse[];
    reservationExpiresAt: string;
    createdAt: string;
    updatedAt: string;
}


export interface CustomerOrderResponse {
    orderNumber: string;
    orderStatus: OrderStatus;
    paymentStatus: PaymentStatus | null;
    branchName: string;
    branchAddress: string;
    pickupDate: string;
    pickupStartTime: string;
    pickupEndTime: string;
    pickupType: PickupType;
    customerName: string;
    maskedCustomerPhone: string;
    items: OrderItemResponse[];
    subtotal: number;
    taxAmount: number;
    priorityCharge: number;
    totalAmount: number;
    reservationExpiresAt: string;
    createdAt: string;
    updatedAt: string;
}


/*
 * Compact response used by My Orders. Detailed items are loaded only after
 * the customer opens one order.
 */
export interface CustomerOrderSummaryResponse {
    orderNumber: string;
    orderStatus: OrderStatus;
    branchName: string;
    pickupDate: string;
    pickupStartTime: string;
    pickupEndTime: string;
    pickupType: PickupType;
    totalAmount: number;
    createdAt: string;
    updatedAt: string;
}
