export type ProductPerformanceState =
    | "STAR"
    | "GROWING"
    | "CORE"
    | "NICHE"
    | "DECLINING"
    | "NEW_INSUFFICIENT_DATA";


export interface ProductIntelligenceSummary {
    productsWithSales: number;
    totalQuantitySold: number;
    grossItemRevenue: number;
    starProducts: number;
    growingProducts: number;
    decliningProducts: number;
}


export interface ProductIntelligenceItem {
    productId: number;
    productCode: string;
    productName: string;
    categoryId: number;
    categoryCode: string;
    categoryName: string;
    orderCount: number;
    quantitySold: number;
    grossItemRevenue: number;
    uniqueCustomers: number;
    activeSalesDays: number;
    orderPenetrationPercent: number;
    activeDayConsistencyPercent: number;
    previousRevenue: number;
    previousQuantitySold: number;
    revenueGrowthPercent: number;
    state: ProductPerformanceState;
    stateReason: string;
}


export interface ProductIntelligenceResponse {
    fromDate: string;
    toDate: string;
    comparisonFromDate: string;
    comparisonToDate: string;
    branchId: number | null;
    summary: ProductIntelligenceSummary;
    products: ProductIntelligenceItem[];
}
