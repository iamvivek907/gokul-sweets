export type InventoryUnit = "PIECE" | "GRAM" | "CAPACITY_POINT";

export interface InventoryCheckRequestItem {
    productId: number;
    quantity: number | null;
    weightGrams: number | null;
}

export interface InventoryCheckRequest {
    serviceDate: string;
    items: InventoryCheckRequestItem[];
}

export interface InventoryCheckItem {
    productId: number;
    productName: string;
    inventoryUnit: InventoryUnit | null;
    requestedQuantity: number;
    availableQuantity: number;
    orderable: boolean;
    unavailableReason: string | null;
}

export interface InventoryCheckResponse {
    enforcementEnabled: boolean;
    requestedDate: string;
    orderable: boolean;
    suggestedDate: string | null;
    callBranchRecommended: boolean;
    items: InventoryCheckItem[];
}
