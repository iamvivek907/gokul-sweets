export type LeaveApprovalAction =
    | "SUBMITTED"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "RESUBMITTED"
    | "CANCELLED";


export type LeaveApprovalStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export interface LeaveApprovalHistoryItem {

    id: number;

    action: LeaveApprovalAction;

    fromStatus: LeaveApprovalStatus | null;

    toStatus: LeaveApprovalStatus;

    actorName: string;

    comment: string | null;

    createdAt: string;
}
