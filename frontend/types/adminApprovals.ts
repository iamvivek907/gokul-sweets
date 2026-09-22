export type ApprovalRequestType =
    | "LEAVE"
    | "ATTENDANCE"
    | "PAYMENT";


export type ApprovalRequestStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export type ApprovalRequestAction =
    | "SUBMITTED"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "RESUBMITTED"
    | "CANCELLED";


export interface AdminApprovalRequest {

    id: number;

    requestNumber: string;

    requestType: ApprovalRequestType;

    status: ApprovalRequestStatus;

    staffUserId: number;

    staffName: string;

    staffUsername: string;

    branchId: number;

    branchCode: string;

    branchName: string;

    title: string;

    summary: string | null;

    workflowVersion: number;

    submittedAt: string;

    resolvedAt: string | null;

    createdAt: string;

    updatedAt: string;
}


export interface AdminApprovalHistory {

    id: number;

    action: ApprovalRequestAction;

    fromStatus: ApprovalRequestStatus | null;

    toStatus: ApprovalRequestStatus;

    actorStaffUserId: number;

    actorName: string;

    comment: string | null;

    createdAt: string;
}


export interface AdminApprovalDetail {

    request: AdminApprovalRequest;

    history: AdminApprovalHistory[];
}


export interface AdminApprovalCounts {

    pending: number;

    sentBack: number;

    approved: number;

    rejected: number;

    cancelled: number;

    total: number;
}


export interface AdminApprovalBranchOption {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


export interface AdminApprovalOptions {

    branches: AdminApprovalBranchOption[];

    requestTypes: ApprovalRequestType[];

    statuses: ApprovalRequestStatus[];
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
