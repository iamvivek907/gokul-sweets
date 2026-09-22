import {apiClient} from "@/services/apiClient";
import type {InventoryCheckRequest, InventoryCheckResponse} from "@/types/inventory";

export function checkInventory(
    branchId: number,
    request: InventoryCheckRequest,
    signal?: AbortSignal
): Promise<InventoryCheckResponse> {
    return apiClient<InventoryCheckResponse>(
        `/api/branches/${branchId}/inventory/check`,
        {
            method: "POST",
            body: JSON.stringify(request),
            signal
        }
    );
}
