export type CustomerVerificationStatus =
    | "UNVERIFIED"
    | "VERIFIED"
    | "MERGED";


export interface AdminCustomerDirectoryItem {

    id: number;

    latestName: string | null;

    normalizedPhone: string;

    verificationStatus:
        CustomerVerificationStatus;

    firstSeenAt: string;

    lastSeenAt: string;

    orderCount: number;

    completedPurchaseCount: number;

    lifetimeSpend: number;

    lastPurchaseAt: string | null;
}


export interface AdminCustomerDirectoryResponse {

    content:
        AdminCustomerDirectoryItem[];

    page: number;

    size: number;

    totalElements: number;

    totalPages: number;
}


export interface AdminCustomerDetail {

    id: number;

    latestName: string | null;

    normalizedPhone: string;

    verificationStatus:
        CustomerVerificationStatus;

    firstSeenAt: string;

    lastSeenAt: string;

    orderCount: number;

    completedPurchaseCount: number;

    lifetimeSpend: number;

    averageOrderValue: number;

    lastPurchaseAt: string | null;
}


export interface AdminCustomerOrderHistoryItem {

    orderId: number;

    orderNumber: string;

    branchId: number;

    branchName: string;

    pickupDate: string;

    pickupStartTime: string;

    pickupEndTime: string;

    pickupType: string;

    orderStatus: string;

    subtotal: number;

    taxAmount: number;

    rebateDiscountAmount: number;

    totalAmount: number;

    createdAt: string;
}


export interface AdminCustomerOrderHistoryResponse {

    content:
        AdminCustomerOrderHistoryItem[];

    page: number;

    size: number;

    totalElements: number;

    totalPages: number;
}
