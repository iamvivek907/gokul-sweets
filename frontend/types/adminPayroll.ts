export type PayrollApprovalStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export interface PayrollBranchOption {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


export interface PayrollOptions {

    branches: PayrollBranchOption[];
}


export interface PayrollSummary {

    staffUserId: number;

    staffName: string;

    totalEarned: number;

    committedPayments: number;

    availableToRequest: number;
}


export interface PayrollEarning {

    id: number;

    attendanceId: number;

    branchId: number;

    branchName: string;

    earningDate: string;

    attendanceType:
        | "PRESENT"
        | "HALF_DAY"
        | "ABSENT";

    rateSnapshot: number;

    amount: number;

    createdAt: string;
}


export interface PayrollPaymentRequest {

    id: number;

    approvalRequestNumber: string;

    status: PayrollApprovalStatus;

    workflowVersion: number;

    branchId: number;

    branchName: string;

    amount: number;

    note: string | null;

    submittedAt: string;

    resolvedAt: string | null;

    createdAt: string;

    updatedAt: string;
}


export interface CreatePayrollPaymentRequest {

    branchId: number;

    amount: number;

    note: string | null;
}


export interface UpdatePayrollPaymentRequest {

    branchId: number;

    amount: number;

    note: string | null;
}


export interface StaffCompensation {

    id: number;

    staffUserId: number;

    effectiveFrom: string;

    dailyRate: number;

    halfDayRate: number;

    createdByName: string;

    createdAt: string;
}


export interface SetCompensationPayload {

    effectiveFrom: string;

    dailyRate: number;

    halfDayRate: number;
}


export interface SpringPage<T> {

    content: T[];

    totalElements: number;

    totalPages: number;

    number: number;

    size: number;

    first: boolean;

    last: boolean;
}
