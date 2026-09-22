export type InventoryAutomationMode =
    | "SUGGEST_ONLY"
    | "CREATE_DRAFT"
    | "AUTO_APPROVE_GUARANTEED";

export type InventorySeasonalMode =
    | "ALWAYS"
    | "WINDOW_ONLY"
    | "MANUAL_ONLY";

export interface AutomationWindow {
    id?: number;
    name: string;
    startDate: string;
    endDate: string;
    active: boolean;
}

export interface InventoryAutomationRule {
    branchProductId: number;
    productId: number;
    productCode: string;
    productName: string;
    categoryName: string;
    inventoryUnit: "GRAM" | "PIECE" | "CAPACITY_POINT";
    onlineEnabled: boolean;
    automationMode: InventoryAutomationMode;
    guaranteedQuantity: number;
    forecastEnabled: boolean;
    lookbackWeeks: number;
    minimumHistoryDays: number;
    demandMultiplier: number;
    maximumSuggestedQuantity: number | null;
    availableDaysMask: number;
    seasonalMode: InventorySeasonalMode;
    generationHorizonDays: number | null;
    active: boolean;
    windows: AutomationWindow[];
}

export interface InventoryAutomationRun {
    id: number;
    branchId: number;
    fromDate: string;
    throughDate: string;
    triggerType: "MANUAL" | "SCHEDULED";
    status: "RUNNING" | "COMPLETED" | "PARTIAL" | "FAILED";
    createdCount: number;
    updatedCount: number;
    suggestedCount: number;
    skippedCount: number;
    errorCount: number;
    initiatedBy: string;
    startedAt: string;
    completedAt: string | null;
    errorSummary: string | null;
}

export interface InventoryAutomationWorkspace {
    branchId: number;
    rules: InventoryAutomationRule[];
    recentRuns: InventoryAutomationRun[];
}

export interface InventoryAutomationRuleRequest {
    automationMode: InventoryAutomationMode;
    guaranteedQuantity: number;
    forecastEnabled: boolean;
    lookbackWeeks: number;
    minimumHistoryDays: number;
    demandMultiplier: number;
    maximumSuggestedQuantity: number | null;
    availableDaysMask: number;
    seasonalMode: InventorySeasonalMode;
    generationHorizonDays: number | null;
    active: boolean;
    windows: AutomationWindow[];
}
