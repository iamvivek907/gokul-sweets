import {apiClient} from "@/services/apiClient";
import type {CartItem} from "@/types/cart";
import type {PickupSlot} from "@/types/pickup";

export interface SlotAvailability {
    slot: PickupSlot;
    normalAvailable: boolean;
    priorityAvailable: boolean;
    reason: string | null;
    code?: string | null;
    issues?: ItemAvailability[];
}

export interface ItemAvailability {
    productId: number; productName: string; unit: "GRAM" | "PIECE"; requestedQuantity: number;
    availableQuantity: number | null; available: boolean; code: string | null; reason: string | null; expectedReadyAt: string | null;
}
export interface CartAvailability {
    fulfilmentType: "PICKUP";
    today: string;
    maximumDate: string;
    dates: {date: string; available: boolean; slots: SlotAvailability[]; items?: ItemAvailability[]; reason?: string | null}[];
}

export function availabilityItems(items: CartItem[]) {
    return items.map(item => ({
        productId: item.product.id,
        quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
        weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null
    }));
}

export function checkCartAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal
) {
    return apiClient<CartAvailability>(`/api/branches/${branchId}/availability`, {
        method: "POST", signal, body: JSON.stringify({startDate, days, items, fulfilmentType: "PICKUP"})
    });
}
