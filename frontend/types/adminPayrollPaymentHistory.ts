export type PayrollPaymentApprovalAction =
    | "SUBMITTED"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "RESUBMITTED"
    | "CANCELLED";


export type PayrollPaymentApprovalStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export interface PayrollPaymentApprovalHistoryItem {

    id: number;

    action: PayrollPaymentApprovalAction;

    fromStatus: PayrollPaymentApprovalStatus | null;

    toStatus: PayrollPaymentApprovalStatus;

    actorName: string;

    comment: string | null;

    createdAt: string;
}
