export type DemandForecastConfidence =
    | "LOW"
    | "MEDIUM"
    | "HIGH";


export interface DemandForecastItem {
    productId: number;
    productCode: string;
    productName: string;
    categoryId: number;
    categoryCode: string;
    categoryName: string;
    recommendedQuantity: number;
    lowerQuantity: number;
    upperQuantity: number;
    recentFourWeekAverage: number;
    previousEightWeekAverage: number;
    trendPercent: number;
    variabilityPercent: number;
    weeksObserved: number;
    weeksWithSales: number;
    confidence: DemandForecastConfidence;
    explanation: string;
}


export interface DemandForecastSummary {
    productsForecasted: number;
    recommendedUnits: number;
    lowerUnits: number;
    upperUnits: number;
    highConfidenceProducts: number;
    mediumConfidenceProducts: number;
    lowConfidenceProducts: number;
}


export interface DemandForecastResponse {
    targetDate: string;
    branchId: number;
    branchName: string;
    historyWeeks: number;
    summary: DemandForecastSummary;
    products: DemandForecastItem[];
}
