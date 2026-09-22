export type BusinessInsightSeverity =
    | "INFO"
    | "WATCH"
    | "ACTION";


export type BusinessInsightType =
    | "SALES_DROP"
    | "SALES_GROWTH"
    | "PRODUCT_DECLINE"
    | "PRODUCT_GROWTH"
    | "CUSTOMER_AT_RISK"
    | "CUSTOMER_LAPSED"
    | "CUSTOMER_REACTIVATED"
    | "BASKET_OPPORTUNITY"
    | "DEMAND_VOLATILITY"
    | "BRANCH_CONCENTRATION";


export interface BusinessInsight {
    type: BusinessInsightType;
    severity: BusinessInsightSeverity;
    title: string;
    message: string;
    evidence: string;
    entityType: string;
    entityId: number | null;
    entityName: string | null;
    primaryMetric: number | null;
    primaryMetricLabel: string | null;
}


export interface BusinessInsightsSummary {
    totalInsights: number;
    actionInsights: number;
    watchInsights: number;
    infoInsights: number;
}


export interface BusinessInsightsResponse {
    fromDate: string;
    toDate: string;
    branchId: number | null;
    summary: BusinessInsightsSummary;
    insights: BusinessInsight[];
}
