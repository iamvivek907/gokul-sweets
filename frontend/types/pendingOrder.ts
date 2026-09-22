export interface PendingOrderSession {
    orderId: number;
    orderNumber: string;
    orderStatus: string;

    branchId: number;
    pickupSlotId: number;

    totalAmount: number;

    reservationExpiresAt: string;

    createdAt: string;

    cartFingerprint: string;
}
