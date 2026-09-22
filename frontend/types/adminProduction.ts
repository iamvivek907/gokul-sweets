export type ProductionPriority =
    | "DISCREPANCY"
    | "CRITICAL"
    | "DELAYED"
    | "NEEDS_PRODUCTION"
    | "FORECAST_TOP_UP"
    | "READY";

export interface ProductionPlanSummary {
    totalItems: number;
    criticalItems: number;
    needsProductionItems: number;
    forecastTopUpItems: number;
    readyItems: number;
    delayedItems: number;
    discrepancyItems: number;
}

export interface ProductionPlanItem {
    branchProductId: number;
    productId: number;
    productCode: string;
    productName: string;
    categoryName: string;
    inventoryUnit: "GRAM" | "PIECE" | "CAPACITY_POINT";
    controlMode: string;
    allocationStatus: string;
    priority: ProductionPriority;
    approvedQuantity: number;
    heldQuantity: number;
    committedQuantity: number;
    outstandingConfirmedQuantity: number;
    readyQuantity: number;
    fulfilledQuantity: number;
    wastedQuantity: number;
    physicalOnHandQuantity: number;
    safetyBufferQuantity: number;
    forecastQuantity: number;
    forecastConfidence: string | null;
    minimumToPrepareQuantity: number;
    suggestedToPrepareQuantity: number;
    expectedReadyAt: string | null;
    note: string | null;
}

export interface ProductionPlan {
    branchId: number;
    serviceDate: string;
    generatedAt: string;
    summary: ProductionPlanSummary;
    items: ProductionPlanItem[];
}

export type ProductionAction =
    | "PRODUCED"
    | "WASTAGE"
    | "ADJUSTMENT";
