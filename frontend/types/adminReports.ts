export interface ReportBranchOption {
    id: number;
    code: string;
    name: string;
}


export interface ExecutiveDashboardOptions {
    branches: ReportBranchOption[];
}


export interface ExecutiveDashboardKpis {
    revenue: number;
    completedOrders: number;
    unitsSold: number;
    uniqueCustomers: number;
    averageOrderValue: number;
    discountAmount: number;
    revenueChangePercent: number;
    ordersChangePercent: number;
    customersChangePercent: number;
}


export interface ExecutiveDashboardTrendPoint {
    date: string;
    revenue: number;
    orders: number;
    unitsSold: number;
    uniqueCustomers: number;
}


export interface ExecutiveDashboardBranch {
    branchId: number;
    branchCode: string;
    branchName: string;
    revenue: number;
    orders: number;
    unitsSold: number;
}


export interface ExecutiveDashboardHourly {
    hour: number;
    orders: number;
    unitsSold: number;
    revenue: number;
}


export interface ExecutiveDashboardHighlights {
    strongestDay: string | null;
    strongestDayRevenue: number;
    strongestBranchId: number | null;
    strongestBranchName: string | null;
    strongestBranchRevenue: number;
    peakPickupHour: number | null;
    peakPickupOrders: number;
    topProductId: number | null;
    topProductName: string | null;
    topProductQuantity: number;
    topProductRevenue: number;
}


export interface ExecutiveDashboardResponse {
    fromDate: string;
    toDate: string;
    comparisonFromDate: string;
    comparisonToDate: string;
    branchId: number | null;
    kpis: ExecutiveDashboardKpis;
    revenueTrend: ExecutiveDashboardTrendPoint[];
    branches: ExecutiveDashboardBranch[];
    hourlyDemand: ExecutiveDashboardHourly[];
    highlights: ExecutiveDashboardHighlights;
}
