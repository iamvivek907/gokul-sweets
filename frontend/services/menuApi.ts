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


export async function getMenu(
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