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
    | "READY_FOR_DELIVERY"
    | "OUT_FOR_DELIVERY"
    | "DELIVERED"
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
    quoteToken?: string;
    rewardCode?: string | null;
    offerCode?: string | null;
}

export interface CheckoutQuote {
    items: {name: string; unitPrice: string; taxRate: string; taxAmount: string; total: string}[];
    subtotal: string;
    taxAmount: string;
    priorityCharge: string;
    convenienceFee?: string;
    convenienceFeeTax?: string;
    paymentFee?: string;
    paymentFeeTax?: string;
    paymentFeeRate?: string;
    totalAmount: string;
    currency: string;
    expiresAt: string;
    token: string;
}


export interface UpdatePendingOrderRequest {
    pickupSlotId: number;
    pickupType: PickupType;
    items: CreateOrderItemRequest[];
    quoteToken?: string;
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
    customerOrderNumber?: number | null;
    branchId: number;
    pickupSlotId: number;
    pickupDate: string;
    pickupStartTime: string;
    pickupEndTime: string;
    customerName: string;
    customerPhone: string;
    pickupType: PickupType;
    priorityCharge: number;
    convenienceFee?: number;
    convenienceFeeTax?: number;
    paymentFee?: number;
    paymentFeeTax?: number;
    paymentFeeRate?: number;
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
    loyaltyDiscount?: number;
    loyaltyCoins?: number;
    loyaltyRewardCode?: string | null;
    rebateDiscountAmount?: number;
    deliveryFee?: number;
    orderNumber: string;
    customerOrderNumber?: number | null;
    orderStatus: OrderStatus;
    paymentStatus: PaymentStatus | null;
    branchName: string;
    branchAddress: string | null;
    branchFssaiLicenceNumber?: string | null;
    branchPhone?: string | null;
    estimatedReadyAt?: string | null;
    delayReason?: string | null;
    delayReportedAt?: string | null;
    pickupDate: string | null;
    pickupStartTime: string | null;
    pickupEndTime: string | null;
    pickupType: PickupType | null;
    fulfillmentType: "PICKUP" | "DELIVERY";
    deliveryDate: string | null;
    deliveryStartTime: string | null;
    deliveryEndTime: string | null;
    deliveryAddressLine: string | null;
    deliveryLocality: string | null;
    deliveryPostalCode: string | null;
    customerName: string;
    maskedCustomerPhone: string;
    items: OrderItemResponse[];
    subtotal: number;
    taxAmount: number;
    priorityCharge: number;
    convenienceFee?: number;
    convenienceFeeTax?: number;
    paymentFee?: number;
    paymentFeeTax?: number;
    paymentFeeRate?: number;
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
    customerOrderNumber?: number | null;
    orderStatus: OrderStatus;
    branchId: number;
    branchName: string;
    estimatedReadyAt?: string | null;
    delayReportedAt?: string | null;
    pickupDate: string | null;
    pickupStartTime: string | null;
    pickupEndTime: string | null;
    pickupType: PickupType | null;
    fulfillmentType: "PICKUP" | "DELIVERY";
    deliveryDate: string | null;
    deliveryStartTime: string | null;
    deliveryEndTime: string | null;
    totalAmount: number;
    createdAt: string;
    updatedAt: string;
}
