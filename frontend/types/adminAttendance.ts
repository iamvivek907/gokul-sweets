export type AttendanceType =
    | "PRESENT"
    | "HALF_DAY"
    | "ABSENT";


export type AttendanceStatus =
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "CANCELLED";


export type AttendanceApprovalAction =
    | "SUBMITTED"
    | "APPROVED"
    | "REJECTED"
    | "SENT_BACK"
    | "RESUBMITTED"
    | "CANCELLED";


export interface AttendanceBranchOption {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


export interface AttendanceOptions {

    branches: AttendanceBranchOption[];

    attendanceTypes: AttendanceType[];
}


export interface AttendanceItem {

    id: number;

    approvalRequestNumber: string;

    status: AttendanceStatus;

    workflowVersion: number;

    branchId: number;

    branchCode: string;

    branchName: string;

    attendanceDate: string;

    attendanceType: AttendanceType;

    checkInTime: string | null;

    checkOutTime: string | null;

    note: string | null;

    submittedAt: string;

    resolvedAt: string | null;

    createdAt: string;

    updatedAt: string;
}


export interface AttendanceApprovalHistoryItem {

    id: number;

    action: AttendanceApprovalAction;

    fromStatus: AttendanceStatus | null;

    toStatus: AttendanceStatus;

    actorName: string;

    comment: string | null;

    createdAt: string;
}


export interface CreateAttendancePayload {

    branchId: number;

    attendanceDate: string;

    attendanceType: AttendanceType;

    checkInTime: string | null;

    checkOutTime: string | null;

    note: string | null;
}


export interface UpdateAttendancePayload {

    branchId: number;

    attendanceDate: string;

    attendanceType: AttendanceType;

    checkInTime: string | null;

    checkOutTime: string | null;

    note: string | null;
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
