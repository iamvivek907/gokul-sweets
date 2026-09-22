export type BasketPairStrength =
    | "STRONG"
    | "MODERATE"
    | "WEAK"
    | "INSUFFICIENT_DATA";


export interface BasketAnalysisSummary {
    completedOrders: number;
    productsInOrders: number;
    pairRelationships: number;
    strongPairs: number;
    moderatePairs: number;
}


export interface BasketPair {
    productAId: number;
    productACode: string;
    productAName: string;
    productBId: number;
    productBCode: string;
    productBName: string;
    pairOrderCount: number;
    productAOrderCount: number;
    productBOrderCount: number;
    totalCompletedOrders: number;
    supportPercent: number;
    confidenceAToBPercent: number;
    confidenceBToAPercent: number;
    lift: number;
    strength: BasketPairStrength;
    explanation: string;
}


export interface BasketAnalysisResponse {
    fromDate: string;
    toDate: string;
    branchId: number | null;
    summary: BasketAnalysisSummary;
    pairs: BasketPair[];
}
