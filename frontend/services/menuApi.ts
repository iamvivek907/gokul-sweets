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
    );
}
// Consume a launch prefetch once; later visits always reload live availability.
const warmedMenus = new Map<number, {started: number; request: Promise<MenuCategory[]>}>();
export async function warmMenu(branchId: number): Promise<void> {
    if (warmedMenus.has(branchId)) return;
    const request = loadMenu(branchId);
    const entry = {started: Date.now(), request};
    warmedMenus.set(branchId, entry);
    try {
        const categories = await request;
        if (warmedMenus.get(branchId) !== entry) return;
        for (const product of categories.flatMap(category => category.products).slice(0, 8)) {
            if (product.imageUrl) {const image = new Image(); image.src = product.imageUrl;}
        }
    } catch {if (warmedMenus.get(branchId) === entry) warmedMenus.delete(branchId);}
}
export async function getMenu(branchId: number, signal?: AbortSignal): Promise<MenuCategory[]> {
    const warm = warmedMenus.get(branchId);
    warmedMenus.delete(branchId);
    if (warm && Date.now() - warm.started < 15_000) {
        try {
            const result = await warm.request;
            if (signal?.aborted) throw new DOMException("Request aborted", "AbortError");
            return result;
        } catch (error) {if (signal?.aborted) throw error;}
    }
    return loadMenu(branchId, signal);
}
