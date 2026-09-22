export type KotPickupType =
    | "NORMAL"
    | "PRIORITY"
    | "ADMIN_OVERRIDE";


export type KotPrintJobPurpose =
    | "INITIAL_KOT"
    | "REPRINT";


export type KotPrintJobStatus =
    | "QUEUED"
    | "CLAIMED"
    | "PRINTED"
    | "FAILED";


export type KotPrinterStation =
    | "KITCHEN"
    | "SWEETS"
    | "BEVERAGE"
    | "FAST_FOOD"
    | "BILLING";


export interface AdminKotItem {

    id: number;

    productId: number;

    productName: string;

    quantity: number;

    displayOrder: number;
}


export interface AdminKot {

    id: number;

    kotNumber: string;

    orderNumber: string;

    branchId: number;

    branchName: string;

    branchAddress: string;

    pickupDate: string;

    pickupStartTime: string;

    pickupEndTime: string;

    pickupType: KotPickupType;

    startedByStaffId: number;

    startedByStaffName: string;

    createdAt: string;

    firstPrintedAt: string | null;

    lastPrintedAt: string | null;

    printCount: number;

    items: AdminKotItem[];
}


export interface AdminKotReprintResponse {

    printJobId: number;

    kotId: number;

    kotNumber: string;

    orderNumber: string;

    branchId: number;

    purpose: KotPrintJobPurpose;

    station: KotPrinterStation;

    status: KotPrintJobStatus;

    printerId: number | null;

    printerName: string | null;

    attemptCount: number;

    maxAttempts: number;

    queuedAt: string;

    nextAttemptAt: string | null;
}
