import {constrainedPhoneConnection} from "@/lib/mobileConnection";
import {
    apiClient
} from "@/services/apiClient";

import {
    getMockMenu
} from "@/data/mockMenu";

import type {
    MenuCategory
} from "@/types/menu";


const USE_MOCK_MENU =
    process.env.NEXT_PUBLIC_USE_MOCK_MENU
    === "true";


export function stampMenuServiceAvailability(data:MenuCategory[]):MenuCategory[] {
    if(!data.some(category=>category.products.some(product=>product.serviceAvailability)))return data;
    const receivedMonotonic=typeof performance!=="undefined"?performance.now():Date.now();
    return data.map(category=>({...category,products:category.products.map(product=>product.serviceAvailability?{...product,serviceAvailability:{...product.serviceAvailability,receivedMonotonic}}:product)}));
}

async function loadMenu(
    branchId: number,
    signal?: AbortSignal
): Promise<MenuCategory[]> {

    /*
     * =========================================================
     * DEVELOPMENT MOCK
     * =========================================================
     */

    if (USE_MOCK_MENU) {

        await new Promise(
            resolve =>
                setTimeout(
                    resolve,
                    350
                )
        );


        if (
            signal?.aborted
        ) {

            throw new DOMException(
                "Request aborted",
                "AbortError"
            );
        }


        return getMockMenu(
            branchId
        );
    }


    /*
     * =========================================================
     * REAL SPRING BOOT API
     * =========================================================
     */

    return apiClient<MenuCategory[]>(
        `/api/menu?branchId=${encodeURIComponent(branchId)}`,
        {
            method: "GET",
            signal
        }
    ).then(stampMenuServiceAvailability);
}
// Consume a launch prefetch once; later visits always reload live availability.
const WARM_MENU_TTL = 15_000;
const MAX_WARM_MENUS = 3;
type WarmMenu = {started: number; request: Promise<MenuCategory[]>; controller: AbortController; timer: ReturnType<typeof setTimeout>; consumed: boolean; settled: boolean};
const warmedMenus = new Map<number, WarmMenu>();
function discardWarmMenu(branchId: number, entry: WarmMenu): void {
    if (warmedMenus.get(branchId) !== entry) return;
    warmedMenus.delete(branchId);
    clearTimeout(entry.timer);
    entry.controller.abort();
}
export async function warmMenu(branchId: number): Promise<void> {
    for (const [id, entry] of warmedMenus) {
        if (Date.now() - entry.started >= WARM_MENU_TTL) discardWarmMenu(id, entry);
    }
    if (warmedMenus.has(branchId)) return;
    if (warmedMenus.size >= MAX_WARM_MENUS) {
        const oldest = warmedMenus.entries().next().value;
        if (oldest) discardWarmMenu(oldest[0], oldest[1]);
    }
    const controller = new AbortController();
    const request = loadMenu(branchId, controller.signal);
    const entry: WarmMenu = {started: Date.now(), request, controller,
        consumed: false, settled: false, timer: setTimeout(() => discardWarmMenu(branchId, entry), WARM_MENU_TTL)};
    entry.timer.unref?.();
    warmedMenus.set(branchId, entry);
    try {
        const categories = await request;
        if (warmedMenus.get(branchId) !== entry) return;
        if (constrainedPhoneConnection()) return;
        for (const product of categories.flatMap(category => category.products).slice(0, 8)) {
            if (product.imageUrl) {const image = new Image(); image.src = product.imageUrl;}
        }
    } catch {discardWarmMenu(branchId, entry);}
    finally {
        entry.settled = true;
        // Keep the deadline after consumption until the shared request has actually settled.
        if (entry.consumed && warmedMenus.get(branchId) === entry) {
            warmedMenus.delete(branchId);
            clearTimeout(entry.timer);
        }
    }
}
// Abort this consumer promptly without cancelling the shared launch request.
function waitForWarmMenu(request: Promise<MenuCategory[]>, signal?: AbortSignal): Promise<MenuCategory[]> {
    if (!signal) return request;
    if (signal.aborted) return Promise.reject(signal.reason ?? new DOMException("Request aborted", "AbortError"));
    return new Promise((resolve, reject) => {
        const abort = () => {signal.removeEventListener("abort", abort); reject(signal.reason ?? new DOMException("Request aborted", "AbortError"));};
        signal.addEventListener("abort", abort, {once: true});
        request.then(value => {signal.removeEventListener("abort", abort); resolve(value);}, error => {signal.removeEventListener("abort", abort); reject(error);});
    });
}
export async function getMenu(branchId: number, signal?: AbortSignal): Promise<MenuCategory[]> {
    const entry = warmedMenus.get(branchId);
    const warm = entry && !entry.consumed ? entry : undefined;
    if (warm) {
        warm.consumed = true;
        if (warm.settled) {
            warmedMenus.delete(branchId);
            clearTimeout(warm.timer);
        }
    }
    if (warm && Date.now() - warm.started < WARM_MENU_TTL) {
        try {
            const result = await waitForWarmMenu(warm.request, signal);
            if (signal?.aborted) throw new DOMException("Request aborted", "AbortError");
            return result;
        } catch (error) {if (signal?.aborted) throw error;}
    }
    if (warm) discardWarmMenu(branchId, warm);
    return loadMenu(branchId, signal);
}
