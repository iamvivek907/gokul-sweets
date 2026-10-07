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
    dates: {date: string; available: boolean; slots: SlotAvailability[]; items?: ItemAvailability[]; reason?: string | null;
        plannedProduction?: boolean}[];
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
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal, menuPreview = false
) {
    return apiClient<CartAvailability>(`/api/branches/${branchId}/availability${menuPreview ? "?menuPreview=true" : ""}`, {
        method: "POST", signal, body: JSON.stringify({startDate, days, items, fulfilmentType: "PICKUP"})
    });
}

/** Branch capacity only; item validation follows on the selected date. */
export function discoverPickupDates(branchId:number,startDate:string,days:number,signal?:AbortSignal) {
    return apiClient<CartAvailability>(`/api/branches/${branchId}/pickup-discovery?startDate=${encodeURIComponent(startDate)}&days=${days}`,{signal});
}

/** Check the full menu without exceeding the endpoint's 100-item request limit.
 * Sequential batches bound backend work; publish only a complete successful preview.
 * Cart checks continue to use a single authoritative request.
 */
export async function checkMenuAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal
): Promise<CartAvailability> {
    const responses: CartAvailability[] = [];
    for (let offset = 0; offset < items.length; offset += 100) {
        signal?.throwIfAborted();
        responses.push(await checkCartAvailability(branchId, startDate, days, items.slice(offset, offset + 100), signal, true));
    }
    const first = responses[0];
    return {...first, dates: first.dates.map(day => {
        const parts = responses.map(response => response.dates.find(value => value.date === day.date));
        return {...day,
            available: parts.every(part => part?.available === true),
            items: parts.flatMap(part => part?.items ?? []),
            slots: day.slots.map(slot => {
                const matches = parts.map(part => part?.slots.find(value => value.slot.id === slot.slot.id));
                const blocked = matches.find(value => value && !value.normalAvailable && !value.priorityAvailable);
                return {...slot,
                    normalAvailable: matches.every(value => value?.normalAvailable === true),
                    priorityAvailable: matches.every(value => value?.priorityAvailable === true),
                    code: matches.some(value => value?.code === "PICKUP_WINDOW") ? "PICKUP_WINDOW" : blocked?.code ?? slot.code,
                    reason: blocked?.reason ?? slot.reason,
                    issues: matches.flatMap(value => value?.issues ?? [])};
            })};
    })};
}
