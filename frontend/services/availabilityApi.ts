import {getCartSnapshot,parseCart} from "@/lib/cartStorage";
import {mealClaim} from "@/lib/cartMeals";
import {apiClient,ApiError} from "@/services/apiClient";
import type {CartItem,CartState} from "@/types/cart";
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

function readAvailability(data: CartAvailability | CompactMenuAvailability): CartAvailability {
    const itemsValid=(items:ItemAvailability[]|undefined)=>items==null||Array.isArray(items)
        &&items.every(item=>Number.isSafeInteger(item?.productId)&&typeof item.available==="boolean");
    if(!data||!Array.isArray(data.dates))throw new Error("We couldn’t check pickup availability");
    const expanded=expandMenuPreview(data);
    if(!expanded.dates.every(day=>typeof day?.date==="string"&&itemsValid(day.items)&&Array.isArray(day.slots)
        &&day.slots.every(slot=>Number.isSafeInteger(slot?.slot?.id)&&itemsValid(slot.issues)))){
        throw new Error("We couldn’t check pickup availability");
    }
    return expanded;
}

export function availabilityItems(items: CartItem[], adjustedCart?: CartState) {
    const mapped: import("@/types/order").CreateOrderItemRequest[]=items.map(item => ({
        productId: item.product.id,
        quantity: item.product.saleMode === "WEIGHT" ? null : item.quantity,
        weightGrams: item.product.saleMode === "WEIGHT" ? item.weightGrams : null
    }));
    if(adjustedCart||typeof window!=="undefined"){
      const cart=adjustedCart??parseCart(getCartSnapshot());
      if(cart.meals?.length&&cart.items.length===items.length&&items.every(i=>cart.items.some(c=>c.product.id===i.product.id&&c.quantity===i.quantity&&c.weightGrams===i.weightGrams)))mapped[0]={...mapped[0],mealSelections:cart.meals.map(mealClaim)};
    }
    return mapped;
}

export async function checkCartAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal, menuPreview = false, slotId?: number
) {
    const previewQuery = menuPreview
        ? `?menuPreview=true&compact=true&versioned=true${slotId === undefined ? "" : `&slotId=${encodeURIComponent(slotId)}`}`
        : "";
    const data = await apiClient<CartAvailability | CompactMenuAvailability>(`/api/branches/${branchId}/availability${previewQuery}`, {
        method: "POST", signal, body: JSON.stringify({startDate, days, items: items.map(({productId,quantity,weightGrams})=>({productId,quantity,weightGrams})), fulfilmentType: "PICKUP"})
    });
    return {...readAvailability(data), receivedMonotonic: typeof performance === "undefined" ? 0 : performance.now()};
}

/** Server-relative boundary; older servers retain the existing periodic refresh fallback. */
export function menuPreviewDeadline(data: CartAvailability): number | null {
    if (!data.observedAt || !data.recheckAt || !Number.isFinite(data.receivedMonotonic)) return null;
    const remaining = Date.parse(data.recheckAt) - Date.parse(data.observedAt);
    return Number.isFinite(remaining) ? data.receivedMonotonic! + Math.max(0, remaining) : null;
}

/** Branch capacity only; item validation follows on the selected date. */
export async function discoverPickupDates(branchId:number,startDate:string,days:number,signal?:AbortSignal) {
    return readAvailability(await apiClient<CartAvailability>(`/api/branches/${branchId}/pickup-discovery?startDate=${encodeURIComponent(startDate)}&days=${days}`,{signal}));
}

/** Give transient menu reads up to two quiet retries within the caller's deadline. */
export async function retryMenuRead<T>(read:()=>Promise<T>,signal?:AbortSignal):Promise<T>{
    for (const delay of [180, 450, null]) {
        signal?.throwIfAborted();
        try {
            const value = await read();
            signal?.throwIfAborted();
            return value;
        } catch (error) {
            signal?.throwIfAborted();
            const apiError = typeof ApiError === "function" && error instanceof ApiError ? error : null;
            const transient = error instanceof TypeError || apiError?.code === "CONNECTION_FAILED"
                || !!apiError && [408,429,500,502,503,504].includes(apiError.status);
            const retryAfter = apiError?.retryAfterMs ?? 0;
            if (delay === null || !transient || retryAfter > 1500) throw error;
            await new Promise<void>((resolve, reject) => {
                const abort = () => {clearTimeout(timer); reject(signal?.reason);};
                const timer = setTimeout(() => {
                    signal?.removeEventListener("abort", abort);
                    resolve();
                }, Math.max(delay, retryAfter));
                signal?.addEventListener("abort", abort, {once: true});
            });
        }
    }
    throw new Error("Menu read did not complete.");
}

/** Check the full menu without exceeding the endpoint's 100-item request limit.
 * At most two batches run together; publish only a complete successful preview.
 * Cart checks continue to use a single authoritative request.
 */
export async function checkMenuAvailability(
    branchId: number, startDate: string, days: number,
    items: ReturnType<typeof availabilityItems>, signal?: AbortSignal, slotId?: number
): Promise<CartAvailability> {
    const responses: CartAvailability[] = [];
    const controller = new AbortController();
    const abort = () => controller.abort(signal?.reason);
    signal?.throwIfAborted();
    signal?.addEventListener("abort", abort, {once: true});
    try {
        for (let offset = 0; offset < items.length; offset += 200) {
            controller.signal.throwIfAborted();
            const batches = [items.slice(offset, offset + 100), items.slice(offset + 100, offset + 200)].filter(batch => batch.length);
            const complete = await Promise.all(batches.map(async batch => {
                controller.signal.throwIfAborted();
                return retryMenuRead(
                    () => checkCartAvailability(branchId, startDate, days, batch, controller.signal, true, slotId),
                    controller.signal
                );
            }));
            responses.push(...complete);
        }
        controller.signal.throwIfAborted();
    } catch (error) {
        controller.abort(error);
        throw error;
    } finally {
        signal?.removeEventListener("abort", abort);
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
            slots: day.slots.flatMap(slot => {
                const matches = parts.map(part => part?.slots.find(value => value.slot.id === slot.slot.id));
                // A slot removed mid-read cannot be offered from an earlier batch.
                const confirmed = matches.filter((value): value is SlotAvailability => value !== undefined);
                if (confirmed.length !== parts.length) return [];
                const closed = confirmed.find(value => value.code === "PICKUP_WINDOW");
                const blocked = closed ?? confirmed.find(value => !value.normalAvailable && !value.priorityAvailable);
                return [{...slot,
                    slot: {...slot.slot,
                        remainingCapacity: Math.min(...confirmed.map(value => value.slot.remainingCapacity)),
                        priorityEnabled: confirmed.every(value => value.slot.priorityEnabled),
                        priorityRemainingCapacity: Math.min(...confirmed.map(value => value.slot.priorityRemainingCapacity ?? 0))},
                    normalAvailable: confirmed.every(value => value.normalAvailable === true),
                    priorityAvailable: confirmed.every(value => value.priorityAvailable === true),
                    code: blocked?.code ?? slot.code,
                    reason: blocked?.reason ?? slot.reason,
                    issues: confirmed.flatMap(value => value.issues ?? [])}];
            })};
    })};
}
