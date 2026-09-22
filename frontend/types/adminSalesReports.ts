export interface SalesIntelligenceSummary {
    revenue: number;
    completedOrders: number;
    unitsSold: number;
    uniqueCustomers: number;
    averageOrderValue: number;
    discountAmount: number;
    previousRevenue: number;
    previousCompletedOrders: number;
    revenueChangePercent: number;
    ordersChangePercent: number;
}


export interface SalesDailyPoint {
    date: string;
    revenue: number;
    orders: number;
    unitsSold: number;
    uniqueCustomers: number;
}


export interface SalesWeekday {
    isoDayOfWeek: number;
    weekday: string;
    completedOrders: number;
    unitsSold: number;
    revenue: number;
    averageRevenuePerActiveDay: number;
}


export interface SalesCategory {
    categoryId: number;
    categoryCode: string;
    categoryName: string;
    orderCount: number;
    quantitySold: number;
    grossItemRevenue: number;
    revenueSharePercent: number;
}


export interface SalesBranchMix {
    branchId: number;
    branchCode: string;
    branchName: string;
    completedOrders: number;
    unitsSold: number;
    revenue: number;
    revenueSharePercent: number;
}


export interface SalesPickupHour {
    hour: number;
    completedOrders: number;
    unitsSold: number;
    revenue: number;
}


export interface SalesIntelligenceResponse {
    fromDate: string;
    toDate: string;
    comparisonFromDate: string;
    comparisonToDate: string;
    branchId: number | null;
    summary: SalesIntelligenceSummary;
    dailyTrend: SalesDailyPoint[];
    weekdayPerformance: SalesWeekday[];
    categoryContribution: SalesCategory[];
    branchMix: SalesBranchMix[];
    pickupHours: SalesPickupHour[];
}
