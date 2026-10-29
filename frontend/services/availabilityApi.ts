import {getCartSnapshot,parseCart} from "@/lib/cartStorage";
import {mealClaim} from "@/lib/cartMeals";
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
    recheckAt?: string | null;
    observedAt?: string | null;
    receivedMonotonic?: number;
    fulfilmentType: "PICKUP";
    today: string;
    maximumDate: string;
    dates: {date: string; available: boolean; slots: SlotAvailability[]; items?: ItemAvailability[]; reason?: string | null;
        plannedProduction?: boolean}[];
}

type CompactMenuAvailability = Omit<CartAvailability, "dates"> & {
    issueCatalog: ItemAvailability[];
    dates: Array<Omit<CartAvailability["dates"][number], "slots"> & {
        slots: Array<Omit<SlotAvailability, "issues"> & {issueIndexes: number[]}>;
    }>;
};

/** Restore the existing UI contract; accept full responses from older backend deployments. */
function expandMenuPreview(data: CartAvailability | CompactMenuAvailability): CartAvailability {
    if (!("issueCatalog" in data)) return data;
    const {issueCatalog, dates, ...header} = data;
    return {...header, dates: dates.map(date => ({...date, slots: date.slots.map(slot => {
        const {issueIndexes, ...decision} = slot;
        return {...decision, issues: issueIndexes.map(index => {
            if (!Number.isInteger(index) || index < 0 || index >= issueCatalog.length) {
                throw new Error("Invalid menu preview issue index");
            }
            return issueCatalog[index];
        })};
    })}))};
}

export function availabilityItems(items: CartItem[]) {
    const mapped: import("@/types/order").CreateOrderItemRequest[]=items.map(item => ({
        productId: item.product.id,
        quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
        weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null
    }));
    if(typeof window!=="undefined"){
      const cart=parseCart(getCartSnapshot());
      if(cart.meals?.length&&cart.items.length===items.length&&items.every(i=>cart.items.some(c=>c.product.id===i.product.id&&c.quantity===i.quantity&&c.weightGrams===i.weightGrams)))mapped[0]={...mapped[0],mealSelections:cart.meals.map(mealClaim)};
    }
    return mapped;
}

export async function checkCartAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal, menuPreview = false
) {
    const data = await apiClient<CartAvailability | CompactMenuAvailability>(`/api/branches/${branchId}/availability${menuPreview ? "?menuPreview=true&compact=true&versioned=true" : ""}`, {
        method: "POST", signal, body: JSON.stringify({startDate, days, items, fulfilmentType: "PICKUP"})
    });
    return {...expandMenuPreview(data), receivedMonotonic: typeof performance === "undefined" ? 0 : performance.now()};
}

/** Server-relative boundary; older servers retain the existing periodic refresh fallback. */
export function menuPreviewDeadline(data: CartAvailability): number | null {
    if (!data.observedAt || !data.recheckAt || !Number.isFinite(data.receivedMonotonic)) return null;
    const remaining = Date.parse(data.recheckAt) - Date.parse(data.observedAt);
    return Number.isFinite(remaining) ? data.receivedMonotonic! + Math.max(0, remaining) : null;
}

/** Branch capacity only; item validation follows on the selected date. */
export function discoverPickupDates(branchId:number,startDate:string,days:number,signal?:AbortSignal) {
    return apiClient<CartAvailability>(`/api/branches/${branchId}/pickup-discovery?startDate=${encodeURIComponent(startDate)}&days=${days}`,{signal});
}

/** Check the full menu without exceeding the endpoint's 100-item request limit.
 * At most two batches run together; publish only a complete successful preview.
 * Cart checks continue to use a single authoritative request.
 */
export async function checkMenuAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal
): Promise<CartAvailability> {
    const responses: CartAvailability[] = [];
    for (let offset = 0; offset < items.length; offset += 200) {
        signal?.throwIfAborted();
        const batches = [items.slice(offset, offset + 100), items.slice(offset + 100, offset + 200)].filter(batch => batch.length);
        const complete = await Promise.all(batches.map(async batch => {
            signal?.throwIfAborted();
            return checkCartAvailability(branchId, startDate, days, batch, signal, true);
        }));
        responses.push(...complete);
    }
    const first = responses[0];
    const boundaries = responses.map(value => value.recheckAt).filter((value): value is string => !!value).sort((left, right) => Date.parse(left) - Date.parse(right));
    const timed = responses.filter(value => menuPreviewDeadline(value) !== null)
        .sort((left, right) => menuPreviewDeadline(left)! - menuPreviewDeadline(right)!)[0];
    return {...first, recheckAt: timed?.recheckAt ?? boundaries[0] ?? first.recheckAt,
        observedAt: timed?.observedAt, receivedMonotonic: timed?.receivedMonotonic, dates: first.dates.map(day => {
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
