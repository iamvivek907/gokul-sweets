export type InventoryControlMode =
    | "READY_STOCK"
    | "DAILY_PRODUCTION"
    | "SLOT_CAPACITY"
    | "MANUAL";

export type InventoryUnit =
    | "PIECE"
    | "GRAM";

export type InventoryAllocationStatus =
    | "DRAFT"
    | "APPROVED"
    | "READY"
    | "DELAYED"
    | "UNAVAILABLE";

export type InventoryCatalogueFilter =
    | "ALL"
    | "NEEDS_ATTENTION"
    | "NOT_CONFIGURED"
    | InventoryAllocationStatus;

export interface InventoryPolicy {
    id: number;
    branchProductId: number;
    branchId: number;
    productId: number;
    productName: string;
    controlMode: InventoryControlMode;
    inventoryUnit: InventoryUnit;
    onlineEnabled: boolean;
    readyStockRequired: boolean;
    defaultSafetyBuffer: number;
    maximumDailyAllocation: number | null;
    bookingHorizonDays: number;
    productionLeadMinutes: number;
    shelfLifeMinutes: number | null;
}

export interface InventoryAllocation {
    id: number;
    branchProductId: number;
    productId: number;
    productName: string;
    serviceDate: string;
    inventoryUnit: string;
    status: InventoryAllocationStatus;
    approvedQuantity: number;
    readyQuantity: number;
    safetyBufferQuantity: number;
    heldQuantity: number;
    committedQuantity: number;
    fulfilledQuantity: number;
    wastedQuantity: number;
    availableQuantity: number;
    orderable: boolean;
    unavailableReason: string | null;
    forecastQuantity: number | null;
    forecastConfidence: string | null;
    expectedReadyAt: string | null;
    actualReadyAt: string | null;
    approvedBy: string | null;
    approvedAt: string | null;
    note: string | null;
}

export interface InventoryCatalogueItem {
    branchProductId: number;
    productId: number;
    categoryId: number;
    categoryName: string;
    productCode: string;
    productName: string;
    saleMode: "UNIT" | "WEIGHT";
    menuAvailable: boolean;
    policy: InventoryPolicy | null;
    allocation: InventoryAllocation | null;
    needsAttention: boolean;
    attentionCode: string | null;
    attentionMessage: string | null;
}

export interface InventoryCatalogueSummary {
    totalProducts: number;
    onlineEnabled: number;
    ready: number;
    needsAttention: number;
    notConfigured: number;
    delayed: number;
    unavailable: number;
}

export interface InventoryCategoryOption {
    id: number;
    name: string;
}

export interface InventoryCataloguePage {
    branchId: number;
    serviceDate: string;
    content: InventoryCatalogueItem[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    summary: InventoryCatalogueSummary;
    categories: InventoryCategoryOption[];
}

export interface InventoryPolicyRequest {
    controlMode: InventoryControlMode;
    inventoryUnit: InventoryUnit;
    onlineEnabled: boolean;
    readyStockRequired: boolean;
    defaultSafetyBuffer: number;
    maximumDailyAllocation: number | null;
    bookingHorizonDays: number;
    productionLeadMinutes: number;
    shelfLifeMinutes: number | null;
}

export interface BulkAllocationItemRequest {
    branchProductId: number;
    approvedQuantity: number;
    safetyBufferQuantity: number | null;
    forecastQuantity: number | null;
    forecastConfidence: string | null;
    expectedReadyAt: string | null;
    note: string | null;
}

export interface BulkReadinessItemRequest {
    branchProductId: number;
    status: "READY" | "DELAYED" | "UNAVAILABLE";
    readyQuantity: number;
    expectedReadyAt: string | null;
    note: string | null;
}

export interface CompleteInventoryItemRequest {
    branchProductId: number;
    approvedQuantity: number;
    readyQuantity: number;
    markReady?: boolean;
    safetyBufferQuantity: number | null;
    forecastQuantity: number | null;
    forecastConfidence: string | null;
    expectedReadyAt: string | null;
    note: string | null;
}
