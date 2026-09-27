export interface PendingOrderSession {
    orderId: number;
    orderNumber: string;
    orderStatus: string;

    branchId: number;
    pickupSlotId: number | null;
    fulfillmentType?: "PICKUP" | "DELIVERY";

    totalAmount: number;

    reservationExpiresAt: string;

    createdAt: string;

    cartFingerprint: string;
}
