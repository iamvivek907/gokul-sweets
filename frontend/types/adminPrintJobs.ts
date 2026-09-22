export type PrintJobStatus =
    | "QUEUED"
    | "CLAIMED"
    | "PRINTED"
    | "FAILED";


export type PrintJobType =
    | "KOT";


export type PrintJobPurpose =
    | "INITIAL_KOT"
    | "REPRINT";


export type PrinterStation =
    | "KITCHEN"
    | "SWEETS"
    | "BEVERAGE"
    | "FAST_FOOD"
    | "BILLING";


export interface AdminPrintJob {

    id: number;

    branchId: number;

    branchName: string;

    kotId: number;

    kotNumber: string;

    orderNumber: string;

    printerId: number | null;

    printerName: string | null;

    jobType: PrintJobType;

    purpose: PrintJobPurpose;

    station: PrinterStation;

    status: PrintJobStatus;

    copies: number;

    attemptCount: number;

    maxAttempts: number;

    claimedByAgent: string | null;

    claimedAt: string | null;

    claimExpiresAt: string | null;

    queuedAt: string;

    firstAttemptAt: string | null;

    lastAttemptAt: string | null;

    printedAt: string | null;

    failedAt: string | null;

    nextAttemptAt: string | null;

    lastErrorCode: string | null;

    lastErrorMessage: string | null;
}


export interface AdminPrintJobPageResponse {

    jobs: AdminPrintJob[];

    page: number;

    size: number;

    totalElements: number;

    totalPages: number;
}


export interface AdminPrintJobCounts {

    queued: number;

    claimed: number;

    printed: number;

    failed: number;

    permanentlyFailed: number;
}