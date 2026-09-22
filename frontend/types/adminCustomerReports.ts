export type CustomerLifecycleState =
    | "NEW"
    | "ACTIVE"
    | "WATCH"
    | "AT_RISK"
    | "LAPSED"
    | "REACTIVATED";


export type CustomerValueSegment =
    | "NEW"
    | "OCCASIONAL"
    | "REGULAR"
    | "HIGH_VALUE"
    | "VIP";


export interface CustomerIntelligenceSummary {
    customersWithPurchases: number;
    repeatCustomers: number;
    repeatCustomerPercent: number;
    verifiedCustomers: number;
    activeCustomers: number;
    atRiskCustomers: number;
    lapsedCustomers: number;
    reactivatedCustomers: number;
    highValueCustomers: number;
    vipCustomers: number;
    averageLifetimeSpend: number;
}


export interface CustomerIntelligenceItem {
    customerId: number;
    latestName: string | null;
    normalizedPhone: string;
    verificationStatus: string;
    firstPurchaseAt: string;
    lastPurchaseAt: string;
    lifetimeOrders: number;
    lifetimeSpend: number;
    averageOrderValue: number;
    orders30d: number;
    orders90d: number;
    orders365d: number;
    spend30d: number;
    spend90d: number;
    spend365d: number;
    daysSinceLastPurchase: number | null;
    expectedPurchaseGapDays: number | null;
    lastPurchaseGapDays: number | null;
    currentGapRatio: number | null;
    lifecycleState: CustomerLifecycleState;
    valueSegment: CustomerValueSegment;
    lifecycleReason: string;
}


export interface CustomerIntelligencePage {
    content: CustomerIntelligenceItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}


export interface CustomerIntelligenceResponse {
    summary: CustomerIntelligenceSummary;
    customers: CustomerIntelligencePage;
}
