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


export type PaymentStatus =
    | "PENDING"
    | "PAID"
    | "FAILED"
    | "EXPIRED"
    | "REFUND_PENDING"
    | "REFUNDED"
    | "REFUND_FAILED";


export type PickupType =
    | "NORMAL"
    | "PRIORITY"
    | "ADMIN_OVERRIDE";


export type PreparationEligibilityStatus =
    | "SCHEDULED"
    | "ELIGIBLE"
    | "OVERDUE"
    | "NOT_APPLICABLE";


export type PreparationBatchResult =
    | "STARTED"
    | "ALREADY_PREPARING"
    | "NOT_ELIGIBLE"
    | "STATUS_CHANGED"
    | "NOT_FOUND"
    | "BRANCH_MISMATCH"
    | "FAILED";


/*
 * =========================================================
 * GENERAL ORDER LIST
 * =========================================================
 */

export interface AdminOrderSummary {

    orderNumber: string;

    branchId: number;

    branchName: string;

    customerName: string;

    maskedCustomerPhone: string | null;

    pickupDate: string;

    pickupStartTime: string;

    pickupEndTime: string;

    pickupType: PickupType;

    totalAmount: number;

    orderStatus: OrderStatus;

    paymentStatus: PaymentStatus | null;

    createdAt: string;
    estimatedReadyAt?: string | null;
    delayReportedAt?: string | null;
}


export interface AdminOrderPageResponse {

    orders: AdminOrderSummary[];

    page: number;

    size: number;

    totalElements: number;

    totalPages: number;
}


/*
 * =========================================================
 * OPERATIONAL PREPARATION QUEUE
 * =========================================================
 */

export interface AdminOrderQueueItem {

    orderNumber: string;

    branchId: number;

    branchName: string;

    customerName: string;

    maskedCustomerPhone: string | null;

    pickupDate: string;

    pickupStartTime: string;

    pickupEndTime: string;

    pickupType: PickupType;

    totalAmount: number;

    orderStatus: OrderStatus;

    paymentStatus: PaymentStatus | null;

    createdAt: string;

    preparationStatus:
        PreparationEligibilityStatus;

    preparationEligibleAt: string;

    pickupAt: string;

    minutesUntilPickup: number;
}


export interface AdminOrderQueueResponse {

    orders: AdminOrderQueueItem[];

    returnedCount: number;

    limit: number;

    hasMore: boolean;

    generatedAt: string;
}


/*
 * =========================================================
 * OPERATIONAL QUEUE COUNTS
 * =========================================================
 */

export interface AdminOrderQueueCounts {

    overdue: number;

    eligible: number;

    scheduled: number;

    preparing: number;

    ready: number;

    confirmedTotal: number;

    actionableTotal: number;

    generatedAt: string;
}


/*
 * =========================================================
 * BATCH PREPARATION
 * =========================================================
 */

export interface AdminStartNextPreparationRequest {

    branchId: number;

    count: number;
}


export interface AdminStartSelectedPreparationRequest {

    branchId: number;

    orderNumbers: string[];
}


export interface AdminBatchPreparationItem {

    orderNumber: string;

    result: PreparationBatchResult;

    message: string;
}


export interface AdminBatchPreparationResponse {

    requested: number;

    attempted: number;

    started: number;

    skipped: number;

    results: AdminBatchPreparationItem[];

    generatedAt: string;
}


/*
 * =========================================================
 * ORDER DETAIL
 * =========================================================
 */

export interface AdminOrderItem {

    id: number;

    productId: number;

    productName: string;

    saleMode?: "UNIT" | "WEIGHT";

    weightGrams?: number | null;

    quantity: number;

    unitPrice: number;

    taxRate: number;

    taxAmount: number;

    lineTotal: number;
}


export interface AdminOrderDetail {

    orderNumber: string;

    branchId: number;

    branchName: string;

    branchAddress: string;
    estimatedReadyAt?: string | null;
    delayReason?: string | null;
    delayReportedAt?: string | null;

    customerName: string;

    customerPhone: string;

    pickupDate: string;

    pickupStartTime: string;

    pickupEndTime: string;

    pickupType: PickupType;

    orderStatus: OrderStatus;

    paymentStatus: PaymentStatus | null;

    subtotal: number;

    taxAmount: number;

    priorityCharge: number;

    totalAmount: number;

    adminOverride: boolean;

    overrideReason: string | null;

    items: AdminOrderItem[];

    createdAt: string;

    updatedAt: string;
}


/*
 * =========================================================
 * SINGLE ORDER STATUS UPDATE
 * =========================================================
 */

export interface AdminOrderStatusUpdateRequest {

    status: OrderStatus;
}
