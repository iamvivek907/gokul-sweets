export type LeaveRequestStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export interface LeaveBranchOption {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


export interface LeaveOptions {

    branches: LeaveBranchOption[];
}


export interface LeaveRequestItem {

    id: number;

    approvalRequestNumber: string;

    status: LeaveRequestStatus;

    workflowVersion: number;

    branchId: number;

    branchCode: string;

    branchName: string;

    startDate: string;

    endDate: string;

    totalDays: number;

    reason: string;

    submittedAt: string;

    resolvedAt: string | null;

    createdAt: string;

    updatedAt: string;
}


export interface CreateLeaveRequestPayload {

    branchId: number;

    startDate: string;

    endDate: string;

    reason: string;
}


export interface UpdateLeaveRequestPayload {

    branchId: number;

    startDate: string;

    endDate: string;

    reason: string;
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
